Đúng. Danh sách hiện tại đang là **feature checklist**, chưa phải specification. Nếu đưa nguyên danh sách này cho AI code thì rất dễ xảy ra các hiểu sai như:

* “Nearby” là chia sẻ **vị trí realtime liên tục** hay chỉ gửi vị trí hiện tại?
* Người dùng có thể thấy **chính xác vị trí** của nhau hay chỉ khoảng cách?
* “3 km” tính từ đâu?
* Người tắt Nearby có còn xuất hiện với người khác không?
* “Location expiration” áp dụng cho vị trí hay trạng thái Nearby?
* Block xử lý ở client hay server?
* Map marker có chứa lat/lng thật không?
* “Realtime” là GPS update mỗi vài giây hay realtime presence?

Tôi đề xuất chuyển tính năng này thành một **spec có rule rõ ràng**, để AI code dựa vào behavior chứ không tự suy diễn.

---

# 10. Nearby / Tìm bạn quanh đây

## 10.1. Mục đích

Nearby cho phép người dùng tìm những người dùng khác đang **chủ động bật chế độ Nearby** và cho phép chia sẻ vị trí.

Hệ thống tìm những người trong phạm vi **3 km tính từ vị trí hiện tại của người dùng**.

Nearby là một tính năng **opt-in**:

> Người dùng phải chủ động bật Nearby và cấp quyền/chấp thuận chia sẻ vị trí thì mới có thể xuất hiện trong kết quả Nearby.

---

# 10.2. Các trạng thái của Nearby

Mỗi user có trạng thái Nearby:

```text
OFF
ON
```

### Nearby OFF

Khi Nearby = OFF:

* Không xuất hiện trong kết quả Nearby của người khác.
* Không được hiển thị marker trên map của người khác.
* Không cập nhật vị trí phục vụ Nearby.
* Vị trí Nearby trước đó phải được coi là **không còn public**.
* Người dùng vẫn có thể sử dụng các chức năng khác của app bình thường.

### Nearby ON

Khi Nearby = ON:

* Hệ thống kiểm tra quyền vị trí.
* Nếu chưa có quyền → yêu cầu cấp quyền.
* Người dùng phải cho phép chia sẻ vị trí.
* Hệ thống sử dụng vị trí hiện tại để tìm người xung quanh.
* User có thể xuất hiện trong Nearby của những người khác nếu thỏa mãn tất cả điều kiện privacy.

---

# 10.3. Permission và Privacy

Cần phân biệt **quyền truy cập vị trí của hệ điều hành** với **quyền chia sẻ vị trí trong ứng dụng**.

### Android Location Permission

App cần permission để lấy vị trí:

```text
Location permission
├── Granted
└── Denied
```

Permission của hệ điều hành **không đồng nghĩa** với việc user đang chia sẻ vị trí cho người khác.

Ví dụ:

```text
Location permission = Granted
Nearby = OFF
→ Không chia sẻ vị trí
```

và:

```text
Location permission = Granted
Nearby = ON
Location sharing = ON
→ Có thể xuất hiện trên Nearby
```

---

# 10.4. Location Sharing

User có một trạng thái:

```text
locationSharing: boolean
```

### locationSharing = false

* Không đưa vị trí user vào Nearby.
* Không hiển thị user trên map của người khác.
* Không cho người khác xem vị trí thông qua Nearby.
* Có thể vẫn cho app lấy location nếu cần cho chức năng khác, nhưng **không được dùng location đó để public Nearby**.

### locationSharing = true

User cho phép hệ thống sử dụng vị trí cho Nearby.

---

# 10.5. Điều kiện để một user xuất hiện trong Nearby

Một user **chỉ được xuất hiện** nếu thỏa mãn toàn bộ:

```text
Nearby = ON
AND
Location sharing = ON
AND
Location permission = Granted
AND
Location chưa expired
AND
User chưa block người đang xem
AND
Người đang xem chưa block user đó
AND
Khoảng cách <= 3 km
```

Có thể biểu diễn:

```text
visibleInNearby(A, B) =
    A.nearbyEnabled
    && A.locationSharingEnabled
    && A.locationNotExpired
    && !isBlocked(A, B)
    && !isBlocked(B, A)
    && distance(A.location, B.location) <= 3km
```

**Không được chỉ kiểm tra khoảng cách.**

---

# 10.6. Bán kính Nearby

Mặc định:

```text
NEARBY_RADIUS = 3 km
```

Khoảng cách được tính giữa:

```text
currentUser.location
```

và:

```text
nearbyUser.location
```

Khoảng cách nên được tính ở **server/backend** hoặc một service thống nhất, không tin vào khoảng cách do client tự tính để quyết định user có hợp lệ hay không.

Ví dụ:

```text
User A
lat: ...
lng: ...

User B
lat: ...
lng: ...

distance(A, B) = 1.8 km
```

→ B đủ điều kiện xuất hiện với A.

Nếu:

```text
distance(A, B) = 3.2 km
```

→ Không xuất hiện.

---

# 10.7. Tính khoảng cách

Khoảng cách là khoảng cách địa lý giữa hai tọa độ.

Backend có thể sử dụng công thức Haversine hoặc cơ chế geospatial của database.

Kết quả trả về nên có dạng:

```json
{
  "userId": "user_123",
  "distance": 1.24,
  "distanceUnit": "km"
}
```

Frontend **không tự quyết định**:

```text
1.24 km <= 3 km
```

để xác định user có thuộc Nearby hay không.

Backend phải chịu trách nhiệm filter.

---

# 10.8. Không hiển thị vị trí chính xác

Đây là điểm rất quan trọng cần ghi rõ cho AI.

**Nearby không đồng nghĩa với việc user được xem tọa độ chính xác của nhau.**

Mặc định:

```text
User A không được nhận:
lat chính xác của B
lng chính xác của B
```

Thay vào đó frontend nhận thông tin cần thiết để hiển thị Nearby.

Ví dụ:

```json
{
  "userId": "user_123",
  "displayName": "Nam",
  "avatar": "...",
  "distance": 1.2
}
```

Nếu map cần marker, hệ thống phải xác định rõ marker có phải là **approximate location** hay không.

Khuyến nghị specification:

> Marker của người khác phải được xem là vị trí tương đối/xấp xỉ, không được sử dụng để suy ra tọa độ chính xác của người dùng.

---

# 10.9. Hiển thị danh sách Nearby

Nearby screen có thể hiển thị:

```text
Nearby

[Avatar] Nguyễn Văn A
         0.8 km
         [Profile] [Message]

[Avatar] Trần Văn B
         1.4 km
         [Profile] [Message]

[Avatar] Lê Văn C
         2.7 km
         [Profile] [Message]
```

Danh sách chỉ chứa những user vượt qua toàn bộ điều kiện visibility.

Có thể sort:

```text
distance ASC
```

tức người gần nhất trước.

Nếu muốn behavior khác thì phải ghi rõ; AI không nên tự chọn sorting.

---

# 10.10. Map

Nearby có hai cách hiển thị:

```text
List View
Map View
```

Map hiển thị:

* vị trí tương đối của user hiện tại.
* marker của những user đủ điều kiện Nearby.
* không hiển thị marker của user:

  * Nearby OFF
  * Location sharing OFF
  * location expired
  * blocked
  * ngoài bán kính 3 km.

---

# 10.11. Click marker

Khi user click marker:

```text
Marker
   ↓
Nearby user summary
   ↓
View Profile
```

Có thể hiển thị:

```text
Avatar
Name
Distance
```

Sau đó có action:

```text
View Profile
Follow
Add Friend
Message
```

Không được expose:

```text
latitude
longitude
exact address
```

trừ khi sản phẩm có specification riêng cho việc đó.

---

# 10.12. Follow / Friend

Nearby chỉ là **cách tìm user**, không thay đổi relationship model.

Ví dụ:

```text
Nearby
   ↓
Profile
   ↓
Follow / Add Friend
```

Follow/Friend phải sử dụng logic relationship hiện tại của app.

Ví dụ user đã là friend:

```text
Friend
```

thì không hiển thị:

```text
Add Friend
```

mà có thể hiển thị:

```text
Friends
```

Tương tự với Follow.

---

# 10.13. Nhắn tin từ Nearby

User có thể nhắn tin trực tiếp từ Nearby nếu relationship/privacy policy của app cho phép.

Flow:

```text
Nearby
  ↓
Marker / User Card
  ↓
Message
  ↓
Chat screen
```

Nearby **không tự tạo conversation mới** nếu hệ thống chat đã có quy tắc riêng về việc tạo conversation.

---

# 10.14. Block

Block phải được xử lý **hai chiều**.

Ví dụ:

```text
A block B
```

thì:

```text
B không xuất hiện trong Nearby của A
A không xuất hiện trong Nearby của B
```

Tức điều kiện:

```text
blocked(A, B) == true
OR
blocked(B, A) == true
```

→ không hiển thị Nearby.

Không nên chỉ filter block ở frontend.

Backend phải filter trước khi trả dữ liệu.

---

# 10.15. Location Update

“Cập nhật vị trí realtime” cần định nghĩa rõ.

Không nên hiểu là:

> GPS được gửi lên server mỗi khi GPS thay đổi.

Thay vào đó nên đặt rule cụ thể.

Ví dụ MVP:

```text
Nearby = ON
    ↓
App lấy current location
    ↓
Upload location
    ↓
Server lưu location
```

Update khi:

```text
1. User bật Nearby
2. User di chuyển vượt một khoảng nhất định
3. Một khoảng thời gian nhất định đã trôi qua
4. User mở lại Nearby screen
```

Ví dụ specification:

```text
LOCATION_UPDATE_DISTANCE = 50m
LOCATION_UPDATE_INTERVAL = 30s
```

Nhưng **hai con số này chỉ là ví dụ**. Nếu chưa quyết định thì phải ghi:

> TODO: Define location update interval and minimum movement distance.

Không để AI tự chọn.

---

# 10.16. Location Expiration

Đây là phần nên đặc biệt rõ.

Mỗi location phải có:

```text
latitude
longitude
updatedAt
expiresAt
```

Ví dụ:

```text
updatedAt = 10:00
expiresAt = 10:05
```

Nếu:

```text
currentTime > expiresAt
```

thì location được xem là:

```text
expired
```

Location expired:

* Không được xuất hiện trong Nearby.
* Không được hiển thị marker.
* Không được dùng để tính khoảng cách.
* Không được coi là vị trí hiện tại của user.

---

# 10.17. Khi Nearby bị tắt

Khi user chuyển:

```text
ON → OFF
```

cần thực hiện:

```text
nearbyEnabled = false
```

và location public phải trở thành inactive.

Có thể:

```text
locationSharingEnabled = false
```

hoặc giữ setting nhưng không sử dụng location khi Nearby OFF.

Tôi khuyến nghị **không tự động thay đổi setting privacy**.

Ví dụ:

```text
Location sharing = ON
Nearby = OFF
```

sau đó:

```text
Nearby = ON
```

→ Location sharing vẫn ON.

Tức:

```text
Nearby enabled
```

và:

```text
Location sharing enabled
```

là **hai state khác nhau**.

---

# 10.18. Khi user mở Nearby

Flow đề xuất:

```text
Open Nearby
    ↓
Nearby enabled?
    ├── NO → Show "Turn on Nearby"
    │
    └── YES
          ↓
     Check location permission
          ├── DENIED → Request permission
          │
          └── GRANTED
                 ↓
          Check location sharing
                 ├── OFF → Ask user to enable
                 │
                 └── ON
                       ↓
                 Get current location
                       ↓
                 Upload/update location
                       ↓
                 Query users within 3km
                       ↓
                 Apply privacy/block/expiration filters
                       ↓
                 Return Nearby users
```

---

# 10.19. Data model tối thiểu

Để AI code không tự trộn các khái niệm, có thể mô tả riêng:

### User

```ts
User {
  id
  name
  avatar
}
```

### Nearby settings

```ts
NearbySettings {
  userId

  nearbyEnabled: boolean

  locationSharingEnabled: boolean
}
```

### User location

```ts
UserLocation {
  userId

  latitude
  longitude

  updatedAt
  expiresAt
}
```

### Block

```ts
Block {
  blockerId
  blockedUserId
  createdAt
}
```

---

# 10.20. API behavior

Có thể định nghĩa API ở mức business trước, chưa cần ép AI vào framework.

### Enable Nearby

```http
POST /nearby/enable
```

Behavior:

```text
Enable Nearby
→ verify location permission at client
→ verify location sharing
→ obtain current location
→ save/update location
→ mark Nearby ON
```

### Disable Nearby

```http
POST /nearby/disable
```

Behavior:

```text
Nearby OFF
→ user no longer appears in Nearby
→ stop location updates
```

### Get Nearby Users

```http
GET /nearby/users
```

Backend:

```text
current user's location
        ↓
find users within 3 km
        ↓
filter Nearby OFF
        ↓
filter location sharing OFF
        ↓
filter expired locations
        ↓
filter blocked users
        ↓
calculate distance
        ↓
sort by distance
        ↓
return users
```

### Update Location

```http
PUT /nearby/location
```

Payload:

```json
{
  "latitude": 21.0285,
  "longitude": 105.8542
}
```

Backend:

```text
validate
→ authorize user
→ save location
→ update updatedAt
→ update expiresAt
```

---

# 10.21. Privacy rules — phần bắt buộc

Đây nên được đặt thành **hard rules** cho AI:

```text
RULE 1
User không bật Nearby → không xuất hiện trong Nearby.

RULE 2
User không cho phép location sharing → không xuất hiện trong Nearby.

RULE 3
Location expired → không xuất hiện trong Nearby.

RULE 4
User A block User B → A và B không nhìn thấy nhau trong Nearby.

RULE 5
User B block User A → A và B không nhìn thấy nhau trong Nearby.

RULE 6
Distance > 3 km → không xuất hiện.

RULE 7
Client không được quyết định quyền visibility cuối cùng.
Backend phải enforce privacy rules.

RULE 8
Không expose exact location của user khác nếu feature không yêu cầu.

RULE 9
Tắt Nearby phải khiến user biến khỏi Nearby của người khác.

RULE 10
Nearby location không đồng nghĩa với location sharing cho toàn bộ app.
```

---

# 10.22. Những thứ chưa được tự quyết định

Đây là phần rất quan trọng khi đưa cho AI.

Nếu sản phẩm chưa quyết định, **AI không được tự chọn implementation behavior**.

### Cần quyết định:

1. **Location expiration bao lâu?**

   * 5 phút?
   * 10 phút?
   * 30 phút?

2. **Location update interval?**

   * 10 giây?
   * 30 giây?
   * 1 phút?

3. **Movement threshold?**

   * 10 m?
   * 50 m?
   * 100 m?

4. **Có chạy location update khi app ở background không?**

5. **Có cần foreground service trên Android không?**

6. **Marker có hiển thị vị trí approximate hay exact?**

7. **Có hiển thị user hiện tại trên map không?**

8. **Nearby có hoạt động khi app bị đóng hoàn toàn không?**

9. **Có cho phép thay đổi radius không?**

   * Nếu chưa có → hardcode business rule 3 km.

10. **Có cho phép người dùng chọn ai được nhìn thấy mình không?**

    * Everyone
    * Friends
    * Nobody

11. **Follow và Friend có điều kiện gì?**

12. **Message có cho phép người lạ nhắn tin không?**

---

## 10.23. Phiên bản ngắn để đưa thẳng cho AI coding

Nếu mục đích của bạn là **paste vào prompt cho AI**, tôi sẽ dùng bản này thay cho checklist ban đầu:

# Nearby / Tìm bạn quanh đây

## Objective

Nearby allows users to discover other users who have explicitly enabled Nearby and location sharing.

The default search radius is **3 km**.

Nearby is an **opt-in feature**. A user must explicitly enable Nearby and allow location sharing before they can appear in other users' Nearby results.

## Core states

Nearby state:

```ts
nearbyEnabled: boolean
```

Location sharing state:

```ts
locationSharingEnabled: boolean
```

These are separate states.

Example:

```text
nearbyEnabled = false
locationSharingEnabled = true
```

means the user is still allowing location sharing in principle, but is currently not participating in Nearby.

Do not automatically modify `locationSharingEnabled` when Nearby is turned off.

## User location

Store:

```ts
{
  userId,
  latitude,
  longitude,
  updatedAt,
  expiresAt
}
```

An expired location must not be used for Nearby.

```text
currentTime > expiresAt
→ location is expired
→ user must not appear in Nearby
```

## Visibility rules

User A can see User B in Nearby only if ALL conditions are true:

```text
B.nearbyEnabled == true
AND
B.locationSharingEnabled == true
AND
B.location exists
AND
B.location is not expired
AND
A has not blocked B
AND
B has not blocked A
AND
distance(A, B) <= 3 km
```

Backend must enforce these rules.

The frontend must not be trusted to enforce privacy or visibility rules.

## Radius

Nearby radius is fixed at:

```text
3 km
```

Do not make the radius configurable unless explicitly requested.

Distance is calculated from the current user's location to the nearby user's location.

Users farther than 3 km must not be returned by the Nearby API.

## Privacy

Nearby must NOT expose another user's exact latitude/longitude to the client unless explicitly required by another specification.

The UI should primarily receive:

```text
userId
displayName
avatar
distance
```

Map markers should represent approximate/controlled location, not expose exact coordinates.

## Nearby OFF

When the user disables Nearby:

```text
nearbyEnabled = false
```

The user must immediately stop being eligible for Nearby results.

The client should also stop sending location updates for Nearby.

The existing location may remain stored temporarily, but it must not be considered publicly visible or eligible for Nearby while `nearbyEnabled == false`.

## Nearby ON

When the user enables Nearby:

1. Check Android location permission.
2. If permission is missing, request it on the client.
3. Check `locationSharingEnabled`.
4. If location sharing is disabled, ask the user to enable it.
5. Obtain the current location.
6. Upload/update the user's Nearby location.
7. Query users within 3 km.
8. Apply all privacy, block, expiration, and distance rules.
9. Display the resulting users.

## Nearby list

Display users that satisfy all visibility rules.

Default sorting:

```text
distance ASC
```

Nearest users appear first.

Each result may contain:

```ts
{
  userId,
  displayName,
  avatar,
  distance
}
```

## Map

The Nearby map displays markers for eligible Nearby users.

Do not display markers for:

* users with Nearby disabled
* users with location sharing disabled
* users with expired locations
* blocked users
* users outside the 3 km radius

Clicking a marker opens a user summary/profile.

Available actions may include:

```text
View Profile
Follow
Add Friend
Message
```

These actions must reuse the existing relationship/chat business rules. Nearby must not create a separate relationship system.

## Block

Blocking is symmetric for Nearby visibility.

If:

```text
A blocks B
```

then:

```text
A cannot see B in Nearby
B cannot see A in Nearby
```

The same applies if B blocks A.

Block filtering must be performed by the backend.

## Location updates

Nearby location updates are required while Nearby is active.

However, the following values are NOT defined yet and must not be invented by the implementation:

```text
location update interval
minimum movement distance
location expiration duration
background location behavior
```

Mark these values as TODO/configuration until explicitly defined.

## Follow / Friend

Nearby is only a discovery mechanism.

Following or adding a friend from Nearby must use the existing relationship logic.

Do not create a separate Nearby-specific friend/follow system.

## Messaging

A user may start/open a conversation from Nearby if the existing chat/privacy rules allow messaging that user.

Nearby must not bypass existing messaging permissions.

## Important implementation rules

1. Nearby is opt-in.
2. Location sharing and Nearby are separate states.
3. Radius is 3 km.
4. Expired locations are invisible.
5. Blocked users are invisible in both directions.
6. Backend enforces all visibility/privacy rules.
7. Do not expose exact coordinates of other users unless explicitly required.
8. Turning Nearby off removes the user from Nearby eligibility.
9. Nearby does not create a separate friend/follow/chat system.
10. Do not invent unspecified values such as update interval, expiration duration, background tracking behavior, or exact map precision.

## Explicit TODOs

The following must remain configurable/TODO until product requirements define them:

* Location expiration duration
* Location update interval
* Minimum movement distance for location update
* Background location behavior
* Whether exact or approximate marker coordinates are used
* Whether Nearby works when the app is completely closed
* Whether users can restrict visibility to friends only

Bản này khác checklist ban đầu ở chỗ nó đã tách **state → điều kiện visibility → privacy → data → API behavior → UI behavior → những thứ chưa được quyết định**. Với AI coding agent, đây là cấu trúc an toàn hơn nhiều vì nó giảm đáng kể khoảng trống để model tự “sáng tạo requirement”.
