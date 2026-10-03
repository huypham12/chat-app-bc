import os
import json
import tempfile
import torch
import torchaudio
import speech_recognition as sr
from pydub import AudioSegment
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from speechbrain.inference.speaker import SpeakerRecognition

# BỔ SUNG: Khai báo thư viện Firebase
import firebase_admin
from firebase_admin import credentials, auth

os.environ["PATH"] += os.pathsep + r"C:\ffmpeg\ffmpeg-n7.1-latest-win64-gpl-7.1\bin"
app = FastAPI()

# BỔ SUNG: Khởi tạo quyền Admin Firebase từ tệp khóa
print("Đang kết nối Firebase Admin...")
try:
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)
    print("Kết nối Firebase Admin thành công!")
except Exception as e:
    print(f"Lỗi khởi tạo Firebase: {e}")

print("Đang tải mô hình AI...")
spk_model = SpeakerRecognition.from_hparams(
    source="speechbrain/spkrec-ecapa-voxceleb", 
    savedir="pretrained_models"
)
print("Tải mô hình thành công!")

recognizer = sr.Recognizer()

def speech_to_text(audio_path: str) -> str:
    """Hàm phụ trợ chuyển âm thanh thành văn bản"""
    with sr.AudioFile(audio_path) as source:
        audio_data = recognizer.record(source)
        try:
            text = recognizer.recognize_google(audio_data, language="vi-VN")
            return text.lower()
        except sr.UnknownValueError:
            return ""
        except sr.RequestError:
            raise HTTPException(status_code=500, detail="Lỗi kết nối tới dịch vụ STT")

@app.post("/enroll")
async def enroll_voice(audio: UploadFile = File(...)):
    """API Đăng ký: Nhận file audio, trả về vector đặc trưng (Voiceprint)"""
    with tempfile.NamedTemporaryFile(delete=False, suffix=".m4a") as tmp:
        tmp.write(await audio.read())
        tmp_path = tmp.name

    wav_path = tmp_path.replace(".m4a", ".wav")

    try:
        if os.path.getsize(tmp_path) == 0:
            raise Exception("File âm thanh nhận được bị rỗng (0 bytes). Vui lòng kiểm tra lại Micro trên điện thoại/máy ảo!")

        # Chuẩn hóa âm thanh đầu vào
        sound = AudioSegment.from_file(tmp_path)
        sound = sound.set_frame_rate(16000).set_channels(1).set_sample_width(2)
        sound.export(wav_path, format="wav")

        signal, fs = torchaudio.load(wav_path, backend="soundfile")
        embeddings = spk_model.encode_batch(signal)
        
        voiceprint = embeddings.squeeze().tolist()
        return {"status": "success", "voiceprint": voiceprint}
    
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=400, detail=f"Lỗi xử lý âm thanh: {str(e)}")
    
    finally:
        try:
            os.remove(tmp_path)
            if os.path.exists(wav_path):
                os.remove(wav_path)
        except Exception:
            pass  # Bỏ qua nếu file đang bị Windows khóa tạm thời

@app.post("/verify")
async def verify_voice(
    audio: UploadFile = File(...),
    expected_code: str = Form(...),
    saved_voiceprint_json: str = Form(...),
    uid: str = Form(...)  # BỔ SUNG: Nhận UID từ ứng dụng Android gửi lên
):
    """API Đăng nhập: Kiểm tra mã số đọc có đúng không & so sánh giọng nói"""
    with tempfile.NamedTemporaryFile(delete=False, suffix=".m4a") as tmp:
        tmp.write(await audio.read())
        tmp_path = tmp.name

    wav_path = tmp_path.replace(".m4a", ".wav")

    try:
        if os.path.getsize(tmp_path) == 0:
            raise Exception("File âm thanh nhận được bị rỗng (0 bytes). Vui lòng kiểm tra lại Micro trên điện thoại/máy ảo!")

        # Chuẩn hóa âm thanh đầu vào
        sound = AudioSegment.from_file(tmp_path)
        sound = sound.set_frame_rate(16000).set_channels(1).set_sample_width(2)
        sound.export(wav_path, format="wav")

        # BƯỚC 1: Kiểm tra STT
        spoken_text = speech_to_text(wav_path)
        spoken_code = "".join(filter(str.isdigit, spoken_text))
        
        if expected_code not in spoken_code:
            return {
                "status": "failed", 
                "reason": "Mã số không khớp", 
                "spoken": spoken_text
            }

        # BƯỚC 2: So sánh Voiceprint
        saved_list = json.loads(saved_voiceprint_json)
        saved_tensor = torch.tensor(saved_list).unsqueeze(0).unsqueeze(0)

        signal, fs = torchaudio.load(wav_path, backend="soundfile")
        current_tensor = spk_model.encode_batch(signal)

        similarity = spk_model.similarity(current_tensor, saved_tensor)
        score = similarity.item()

        is_match = score > 0.25

        # BƯỚC 3: Xử lý trả kết quả & Cấp Custom Token nếu thành công
        if is_match:
            # Tạo vé thông hành (Token) bằng UID
            custom_token = auth.create_custom_token(uid)
            # Ép kiểu dữ liệu để JSON có thể mã hóa được
            token_str = custom_token.decode('utf-8') if isinstance(custom_token, bytes) else custom_token
            
            return {
                "status": "success",
                "reason": "Xác thực thành công",
                "similarity_score": round(score, 4),
                "custom_token": token_str  # Trả token về cho Android
            }
        else:
            return {
                "status": "failed",
                "reason": "Giọng nói không khớp",
                "similarity_score": round(score, 4)
            }

    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Lỗi hệ thống: {str(e)}")
    
    finally:
        try:
            os.remove(tmp_path)
            if os.path.exists(wav_path):
                os.remove(wav_path)
        except Exception:
            pass  # Bỏ qua nếu file đang bị Windows khóa tạm thời