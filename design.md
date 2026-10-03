Dựa trên toàn bộ feature set 13 cụm mà bạn đưa ra và framework UX trong tài liệu, mình sẽ **phản biện proposal hiện tại trước**, sau đó chốt một architecture có thể triển khai thực tế.

Điểm quan trọng nhất: với bộ tính năng này, đây **không còn đơn thuần là chat app**. Nó là một **Communication + Social + Discovery app**, trong đó Chat vẫn là core nhưng People/Discovery là một trục sản phẩm độc lập.

# 1. Product UX Diagnosis

## 1.1. Nhận định tổng thể

Bộ feature hiện tại có 4 lớp nhu cầu lớn:

```text
COMMUNICATE
├── 1-1 Chat
├── Group Chat
├── Message
├── Media / File
└── Voice / Video

PEOPLE
├── Profile
├── Follow / Friends
├── Block
└── Social relationship

DISCOVER
├── Search
├── Suggested Users
└── Nearby

ACTIVITY
├── Notifications
├── Presence
└── Social events
```

Và một lớp xuyên suốt:

```text
PLATFORM
├── AI
├── Privacy
├── Settings
├── Permissions
├── Offline
└── Push Notification
```

Đây là lý do mình **không khuyến nghị** tổ chức app theo từng database/module hoặc biến mỗi module thành một tab.

---

# 2. Phản biện proposal hiện tại

Proposal:

```text
Chats
Groups
Nearby
More
```

là hợp lý ở giai đoạn prototype vì rất dễ hiểu với developer.

Nhưng nếu triển khai toàn bộ feature list thì nó bắt đầu có vấn đề.

## Vấn đề 1 — Groups bị tách sai cấp độ

Group Chat không phải một destination ngang cấp với Chat.

Về mental model:

```text
Conversation
├── Direct conversation
└── Group conversation
```

Do đó:

```text
Chats
├── Direct
└── Groups
```

hợp lý hơn:

```text
Chats
Groups
```

Nếu tách riêng Groups, người dùng sẽ phải suy nghĩ:

> "Cuộc trò chuyện này nằm ở Chats hay Groups?"

Không cần thiết.

---

## Vấn đề 2 — Nearby quá nổi bật

Nearby là một **discovery mechanism**, không phải một user goal lớn ngang Communication.

Người dùng thường xuyên:

```text
Chat → mỗi ngày
Notifications → thường xuyên
Profile → thường xuyên
Nearby → theo nhu cầu
```

Nếu đưa Nearby thành primary navigation item, app sẽ làm feature tương đối niche trở nên ngang hàng với core communication.

---

## Vấn đề 3 — "More" là nơi dễ biến thành feature graveyard

Nếu để:

```text
More
```

thì sau này rất dễ trở thành:

```text
More
├── Settings
├── Privacy
├── AI
├── Friends
├── Followers
├── Following
├── Blocked
├── Nearby
├── Calls
├── ...
```

Đây là dấu hiệu Information Architecture không còn rõ.

---

## Vấn đề 4 — Social graph chưa có home rõ ràng

Bạn có:

```text
Follow
Followers
Following
Friends
Mutual followers
Suggested users
Mention suggestions
```

Đây thực chất là một hệ thống **People Graph**.

Nếu không gom nó vào một context thống nhất, user sẽ gặp:

```text
Profile → Friends
Profile → Followers
Search → Users
Nearby → Users
Notification → Follow
```

Các feature tồn tại nhưng không tạo thành một mental model thống nhất.

---

# 3. Information Architecture được đề xuất

Mình chốt hierarchy:

```text
APP
│
├── PRIMARY NAVIGATION
│   │
│   ├── Chats
│   ├── Discover
│   ├── Notifications
│   └── Profile
│
├── GLOBAL FEATURES
│   │
│   ├── Global Search
│   ├── New Chat
│   ├── New Group
│   └── AI Assistant
│
├── COMMUNICATION
│   │
│   ├── Direct Conversation
│   ├── Group Conversation
│   ├── Message
│   ├── Media
│   ├── Voice
│   └── Video Call
│
├── PEOPLE / DISCOVERY
│   │
│   ├── Search People
│   ├── Suggested Users
│   ├── Nearby
│   ├── Friends
│   ├── Followers
│   └── Following
│
├── ACTIVITY
│   │
│   └── Notifications
│
└── SETTINGS
    │
    ├── Account
    ├── Privacy
    ├── Notifications
    ├── Presence
    ├── Nearby Privacy
    ├── Blocked Users
    └── AI / Voice settings
```

## Mental model

Người dùng không cần biết database có:

```text
users
conversations
groups
messages
followers
locations
notifications
```

Họ chỉ cần hiểu:

```text
Chat với người khác
↓
Tìm người
↓
Xem hoạt động
↓
Quản lý bản thân
```

Đây chính là cách nên xây IA: theo **user goal**, không theo database.

---

# 4. Navigation Design

## Recommended option

```text
┌─────────────────────────────────────┐
│                                     │
│             CONTENT                 │
│                                     │
│                                     │
├─────────────────────────────────────┤
│                                     │
│   💬        🔎        🔔       👤  │
│ Chats   Discover   Activity  Profile│
└─────────────────────────────────────┘
```

Mình chọn **4 primary destinations**.

### 1. Chats

Icon:

```text
Chat bubble
```

Badge:

```text
Unread messages
```

---

### 2. Discover

Icon:

```text
Search / Explore
```

Badge:

Không cần.

Discover không phải notification surface.

---

### 3. Notifications

Icon:

```text
Bell
```

Badge:

```text
Unread notification count
```

Có thể hiển thị:

```text
9+
```

thay vì con số quá lớn.

---

### 4. Profile

Icon:

```text
User avatar
```

Không badge mặc định.

---

# 5. Vì sao không dùng Chats / Groups / Nearby / More?

So sánh:

| Navigation    | Đánh giá                    |
| ------------- | --------------------------- |
| Chats         | ✅                           |
| Groups        | ❌ nên gộp vào Chats         |
| Nearby        | ⚠️ nên nằm trong Discover   |
| More          | ❌ dễ thành nơi chứa mọi thứ |
| Discover      | ✅                           |
| Notifications | ✅                           |
| Profile       | ✅                           |

Architecture cuối:

```text
Chats
Discover
Notifications
Profile
```

---

# 6. Chats Architecture

## Conversation List

```text
Chats

[ Search ]

Pinned
────────────────
👤 Alice
   See you tomorrow       14:32

👥 Android Team
   Nam: Let's test it     13:21

All
────────────────
👤 Bob
   Thanks!                12:10

👥 Project Group
   Huy: 👍                 11:45
```

Ở trên cùng có thể có filter:

```text
[All] [Unread] [Groups]
```

Không cần tab riêng cho Groups.

---

# 7. New Chat / New Group

FAB:

```text
              (+)
```

Tap:

```text
New
──────────────
New message
New group
```

Hoặc:

```text
Chats
                    ✎
```

Trong đó:

```text
New message
New group
```

Mình nghiêng về **top-right compose icon** hơn FAB nếu app thiên về messaging.

Lý do: Chats là một list screen, hành động tạo conversation là action cấp screen.

---

# 8. Conversation Screen

Đây là màn hình quan trọng nhất toàn app.

```text
┌─────────────────────────────────────┐
│ ←  👤 Alice             ⋮           │
│    Online                            │
├─────────────────────────────────────┤
│                                     │
│        Yesterday                    │
│                                     │
│  Hello!                             │
│                         Hi 👋       │
│                                     │
│  Are you available tomorrow?        │
│                         Yes          │
│                                     │
│                  ❤️ 2              │
│                                     │
├─────────────────────────────────────┤
│ +   Message...              🎤      │
└─────────────────────────────────────┘
```

## Header

Primary:

```text
Back
Avatar
Name
Presence
```

Secondary:

```text
Call
Video
More
```

Không nên nhồi quá nhiều icon.

---

# 9. Message interaction

Tap:

```text
Reaction bar
```

Long press:

```text
┌─────────────────────┐
│ ❤️ 😂 👍 😮 😢      │
├─────────────────────┤
│ Reply               │
│ Forward             │
│ Copy                │
│ Edit                │
│ Delete              │
│ More                │
└─────────────────────┘
```

Nhưng action phải **contextual**.

## Tin nhắn của mình

```text
Reply
React
Forward
Copy
Edit
Delete
Revoke
```

## Tin nhắn người khác

```text
Reply
React
Forward
Copy
Delete
Report
```

Không phải mọi message đều hiển thị mọi action.

---

# 10. Message state

Mỗi message nên có state rõ:

```text
Sending
   ↓
Sent
   ↓
Delivered
   ↓
Read
```

Failure:

```text
Sending
   ↓
Failed
   ↓
Retry
```

Special state:

```text
Edited
Revoked
Deleted
Forwarded
Replied
Reacted
```

Đặc biệt:

```text
Edited
```

nên là metadata nhỏ cạnh timestamp.

Không dùng banner lớn.

---

# 11. Group Chat

Group vẫn nằm trong Chats.

Nhưng Group có thêm:

```text
Group Info
├── Group avatar
├── Group name
├── Members
├── Admins
├── Media
├── Search
├── Notifications
└── Leave group
```

## Group Info

```text
        Group Avatar

      Android Team
      24 members

[Add members]

Members
────────────────

Huy
Admin

Nam
Admin

Alice
Member
```

Admin action chỉ xuất hiện khi user có permission.

---

# 12. Admin permissions

Không nên có một screen cực kỳ phức tạp.

Contextual:

```text
Tap member
↓
Member actions
```

Admin:

```text
Make admin
Remove admin
Remove member
```

Owner:

```text
Transfer ownership
```

Member:

```text
View profile
Message
```

Đây là Progressive Disclosure.

---

# 13. Discover

Đây là thay đổi quan trọng thứ hai.

```text
Discover

[ Search people ]

Suggested for you
────────────────────
👤 Alice
   5 mutual followers
   [Follow]

👤 Bob
   2 mutual followers
   [Follow]

Nearby
────────────────────
People near you

[ Open Nearby ]

Friends
Following
Followers
```

Không nên nhét toàn bộ Friends / Followers / Nearby vào một màn hình dài.

---

# 14. Discover hierarchy

```text
Discover
│
├── Search
│
├── Suggested
│
├── Nearby
│
├── Friends
│
├── Followers
└── Following
```

Đây là secondary navigation.

Có thể dùng:

```text
Top tabs
```

nhưng mình **không khuyến nghị 6 tabs ngang**.

Tốt hơn:

```text
Discover

Search

Suggested
Nearby

People
  Friends
  Followers
  Following
```

---

# 15. Nearby

Nearby nên là một full-screen flow.

```text
Nearby

[ Map ]

       •
   •       •
         •
      •

People nearby

2.1 km   Alice
1.4 km   Bob
2.7 km   Nam
```

## First entry

```text
Nearby

Find people around you.

Your location will be used
to show people nearby.

[Enable Nearby]
```

Sau đó:

```text
Android Location Permission
↓
User grants
↓
Nearby enabled
↓
Map
```

Nếu deny:

```text
Location access is required
to use Nearby.

[Open Settings]
```

Không được đưa user vào màn hình map trống mà không giải thích.

---

# 16. Nearby privacy

Đây là một feature cần thiết kế cẩn thận.

Settings:

```text
Nearby

[●] Show me to nearby users

Radius
3 km

Location sharing
○ While Nearby is enabled
○ Until I turn it off

Location visibility
○ Approximate
○ Exact
```

Nếu product scope không yêu cầu exact location, mình **không khuyến nghị hiển thị vị trí chính xác**.

Hiển thị:

```text
~1.2 km away
```

thay vì:

```text
123 Nguyễn...
```

---

# 17. Location expiration

Nếu có:

```text
Location expiration
```

hãy làm thành setting dễ hiểu:

```text
Share my location for

○ 30 minutes
○ 1 hour
● Until I turn Nearby off
```

Không dùng wording kỹ thuật như:

```text
TTL = 3600
```

---

# 18. Profile

## Own Profile

```text
┌───────────────────────────┐
│          Cover            │
│                           │
│       Avatar              │
└───────────────────────────┘

Huy Pham
@huy

Bio...

120 Followers
85 Following
32 Friends

[Edit Profile]

────────────────

Settings
```

## Other Profile

```text
Avatar

Alice
@alice

Bio...

120 Followers
85 Following

[Follow] [Message]

Mutual followers
```

Action phụ:

```text
⋮
├── Block
├── Report
└── ...
```

---

# 19. Social model

Feature list hiện tại có:

```text
Follow
Followers
Following
Friends
Mutual followers
```

Không nên làm người dùng cảm thấy:

```text
Follow = Friend
```

Cần phân biệt rõ.

Ví dụ:

```text
Follow
```

là asymmetric.

```text
Friend
```

là mutual relationship.

UI:

```text
Following
```

khác:

```text
Friends
```

---

# 20. Block system

Block là cross-cutting feature.

Không chỉ nằm trong Settings.

Entry point:

```text
Profile → ⋮ → Block
```

Sau block:

```text
Profile
Chat
Search
Nearby
Suggested
Notifications
```

đều phải phản ánh trạng thái block.

Ví dụ:

```text
Block
↓
User biến mất khỏi:
- Suggested
- Nearby
- Search relationship surfaces
```

và không thể:

```text
Message
Follow
React
Mention
```

nếu product rule của bạn xác định block chặn toàn bộ interaction.

Đây là một trong những feature cần **global behavior**, không phải chỉ UI.

---

# 21. Notifications

Notification Center:

```text
Notifications

New
────────────────────

❤️ Alice reacted to your message

👤 Bob started following you

💬 Alice mentioned you in a group

👥 You were added to Android Team
```

Notification nên group theo thời gian:

```text
New
Today
Earlier
```

---

# 22. Notification interaction

Notification không chỉ là text.

Ví dụ:

```text
Alice reacted to your message
             ↓
          tap
             ↓
Conversation
             ↓
Scroll to message
             ↓
Highlight message
```

Mention:

```text
Notification
↓
Group Chat
↓
Jump to mentioned message
```

Follow:

```text
Notification
↓
User Profile
```

Group invitation:

```text
Notification
↓
Invitation screen
↓
Accept / Decline
```

Đây là nơi deep-link cực kỳ quan trọng.

---

# 23. Global Search

Mình đề xuất **một Search system thống nhất**.

Entry:

```text
Chats → Search
Discover → Search
```

Có thể tìm:

```text
People
Messages
Conversations
Groups
```

Nhưng kết quả nên contextual.

Ví dụ từ Chats:

```text
Search

Messages
Conversations
People
```

Search trong conversation:

```text
Search messages
```

Không cần mở global search rồi bắt user lọc lại.

---

# 24. AI

Không nên đưa:

```text
AI
```

thành primary tab.

AI hiện có:

```text
Smart Reply
AI Assistant
```

là hai loại interaction khác nhau.

## Smart Reply

Nằm **ngay trong conversation**.

```text
Are you free tomorrow?

[Yes, I am] [Sure] [What time?]
```

Không cần màn hình AI.

## AI Assistant

Nên là global utility.

Entry có thể:

```text
Search / compose / profile
```

hoặc floating contextual entry.

Nhưng không nên chiếm bottom navigation.

---

# 25. Voice Biometrics

Voice Biometrics không nên xuất hiện trong Chat navigation.

Nó thuộc:

```text
Profile
↓
Settings
↓
Security / Identity
```

Ví dụ:

```text
Voice verification

Your voice can be used
to verify your identity.

[Set up voice verification]
```

Đây là utility/security feature.

---

# 26. Video Call

Video call là extension của communication.

Entry:

```text
Chat
├── Voice call
└── Video call
```

Không tạo:

```text
Calls
```

tab riêng.

Trừ khi sau này product phát triển thành một calling platform với:

```text
Call history
Missed calls
Contacts
Conference
Voicemail
```

Hiện tại chưa cần.

---

# 27. Settings

Profile:

```text
Profile
│
└── Settings
    │
    ├── Account
    ├── Privacy
    ├── Notifications
    ├── Appearance
    ├── Chat
    ├── Nearby
    ├── Security
    └── About
```

## Privacy

```text
Privacy

Presence
├── Show online status
└── Show last seen

Messages
└── Who can message me

Nearby
├── Show me nearby
└── Location visibility

Blocked users
```

---

# 28. Presence

Không nên đặt Online/Offline/Last Seen thành các screen.

Nó là **system state**.

Xuất hiện:

```text
Profile
Chat header
Conversation list
Group member list
```

Settings chỉ điều khiển:

```text
Show online status
Show last seen
```

---

# 29. Screen Inventory

## Core screens

```text
1. Chats
2. Conversation
3. Discover
4. Notifications
5. Own Profile
6. Other User Profile
7. Group Conversation
8. Group Info
9. Nearby
```

## Secondary

```text
10. Search
11. Search Results
12. Friends
13. Followers
14. Following
15. Suggested Users
16. Shared Media
17. Message Search
18. Notification Detail / Target
```

## Utility

```text
19. Media Viewer
20. File Viewer
21. Voice Recorder
22. Video Call
23. Voice Call
24. AI Assistant
25. Location Picker
```

## Settings

```text
26. Settings
27. Account
28. Privacy
29. Notification Settings
30. Chat Settings
31. Nearby Settings
32. Security / Voice Verification
33. Blocked Users
```

## Bottom sheets

```text
34. New Chat
35. New Group
36. Attachment Picker
37. Reaction Picker
38. Message Actions
39. Member Actions
40. Conversation Actions
41. Share / Forward
42. Profile Actions
```

## Dialogs

```text
43. Delete message
44. Leave group
45. Remove member
46. Transfer admin
47. Revoke admin
48. Block user
49. Permission explanation
```

Không phải tất cả 49 mục trên đều phải là Activity/Fragment riêng.

Nhiều mục phải là:

```text
Bottom Sheet
Dialog
Full-screen modal
```

---

# 30. User Flow — First-time User

```text
Launch
 ↓
Login / Register
 ↓
Profile setup
 ↓
Chats
 ↓
Empty state
 ↓
"Start a conversation"
 ↓
Search people
 ↓
User Profile
 ↓
Message
 ↓
Conversation
```

Không nên bắt user:

```text
Register
↓
Complete 15 settings
↓
Enable location
↓
Enable notifications
↓
Create username
...
```

Hãy progressive disclosure.

---

# 31. User Flow — New Message

```text
Chats
 ↓
New message
 ↓
Search person
 ↓
Profile preview
 ↓
Message
 ↓
Conversation
 ↓
Send
 ↓
Delivered
 ↓
Read
```

---

# 32. User Flow — Group

```text
Chats
 ↓
New
 ↓
New Group
 ↓
Select members
 ↓
Group name/avatar
 ↓
Create
 ↓
Group Chat
```

Sau này:

```text
Group Chat
 ↓
Group Info
 ↓
Members
 ↓
Member Profile
 ↓
Admin actions
```

---

# 33. User Flow — Nearby

```text
Discover
 ↓
Nearby
 ↓
Nearby disabled?
 ├── Yes
 │    ↓
 │  Explanation
 │    ↓
 │  Location permission
 │    ↓
 │  Enable Nearby
 │
 └── No
      ↓
    Map
      ↓
    Nearby users
      ↓
    User profile
      ↓
    Follow / Friend / Message
```

Block:

```text
Nearby results
↓
Filter blocked users
```

---

# 34. User Flow — Notification

```text
Notification
 ↓
User taps notification
 ↓
Deep link
 ├── Follow → Profile
 ├── Reaction → Message
 ├── Mention → Message
 ├── Group event → Group
 └── Friend event → Profile
```

Đây là flow cần ưu tiên rất cao.

---

# 35. Feature → UI Mapping

| Feature             | UI location          | Entry point      | Priority  |
| ------------------- | -------------------- | ---------------- | --------- |
| Login/Register      | Auth                 | Launch           | Core      |
| Profile             | Profile              | Bottom nav       | Core      |
| 1-1 chat            | Chats                | Conversation     | Core      |
| Group chat          | Chats                | Conversation     | Core      |
| Text                | Composer             | Chat             | Core      |
| Image/video/file    | Attachment sheet     | Chat             | Important |
| Voice message       | Composer             | Chat             | Important |
| Typing              | Chat                 | Conversation     | Important |
| Read receipt        | Message metadata     | Chat             | Important |
| Pin/Mute/Hide       | Conversation actions | Chats            | Important |
| Search conversation | Search               | Chats            | Important |
| Message search      | Search               | Chat             | Important |
| Reply               | Message actions      | Chat             | Core      |
| Edit                | Message actions      | Own message      | Important |
| Delete              | Message actions      | Chat             | Important |
| Revoke              | Message actions      | Own message      | Important |
| Forward             | Message actions      | Chat             | Important |
| Reaction            | Message actions      | Chat             | Core      |
| Group admin         | Member actions       | Group            | Important |
| Follow              | Profile              | Profile/Discover | Core      |
| Friends             | Discover/Profile     | Discover         | Important |
| Followers           | Discover/Profile     | Discover         | Secondary |
| Following           | Discover/Profile     | Discover         | Secondary |
| Suggested users     | Discover             | Discover         | Important |
| Block               | Profile/actions      | Profile          | Core      |
| Notifications       | Notifications        | Bottom nav       | Core      |
| Push                | System               | Global           | Core      |
| Nearby              | Discover             | Discover         | Important |
| Location sharing    | Nearby               | Nearby           | Important |
| AI Smart Reply      | Chat                 | Composer         | Secondary |
| AI Assistant        | Utility              | Global           | Secondary |
| Voice biometrics    | Security             | Settings         | Advanced  |
| Video call          | Chat                 | Chat header      | Important |
| Online              | Presence             | Global           | Core      |
| Last seen           | Presence             | Global           | Important |

---

# 36. States bắt buộc

Đây là phần mình muốn bổ sung mạnh so với một UI prototype thông thường.

Mỗi core screen phải có:

```text
Loading
Success
Empty
Error
Offline
Permission denied
No result
Retry
```

Ví dụ Chats:

```text
Loading
↓
Conversation list
```

Nếu chưa có conversation:

```text
No conversations yet

Start chatting with someone.

[New message]
```

Nếu offline:

```text
You're offline

Messages will be sent when
connection is restored.
```

---

# 37. Offline UX

Chat app bắt buộc phải có offline model.

Message:

```text
Sending
```

nếu mất network:

```text
⚠ Failed to send

[Retry]
```

Không nên silently mất message.

Conversation list cũng nên giữ cached data.

---

# 38. Permission UX

Không hỏi permission ngay khi app launch nếu chưa cần.

Sai:

```text
Launch
↓
Location permission
↓
Notification permission
↓
Camera permission
↓
Microphone permission
```

Đúng:

```text
User taps Nearby
↓
Explain
↓
Location permission
```

và:

```text
User taps voice
↓
Microphone permission
```

Đây là Just-in-time permission.

---

# 39. Visual Design Direction

Mình đề xuất personality:

```text
Modern
Calm
Friendly
Trustworthy
Communication-first
```

Không nên:

```text
Neon
Gaming
Dashboard-heavy
Over-rounded
Excessive gradients
```

---

# 40. Color System

Nếu chưa có brand color, có thể bắt đầu bằng semantic token thay vì khóa cứng màu cụ thể.

```text
Primary
Secondary
Background
Surface
Surface Elevated

Text Primary
Text Secondary
Text Disabled

Divider

Success
Warning
Error

Unread
Online
```

Presence:

```text
Online
```

chỉ cần một accent nhỏ.

Không biến toàn bộ avatar hoặc row thành màu xanh.

---

# 41. Typography

Android:

```text
Display
Headline
Title
Body
Label
Caption
```

Chat ưu tiên:

```text
Body
Caption
```

Ví dụ:

```text
Message       → Body
Username      → Label / Title
Timestamp     → Caption
Status        → Caption
```

Không sử dụng quá nhiều font size.

---

# 42. Spacing

Khuyến nghị base:

```text
4dp
8dp
12dp
16dp
20dp
24dp
32dp
```

Mặc định:

```text
Screen horizontal padding = 16dp
Row vertical padding ≈ 12–16dp
```

Touch target:

```text
≥ 48dp
```

---

# 43. Radius

Không nên bo mọi thứ 24–32dp.

Đề xuất:

```text
Input              12–16dp
Button             12–16dp
Card               16dp
Bottom sheet       24dp top
Dialog             24dp
Avatar             50%
Message bubble     16–20dp
```

Message bubble nên có shape riêng để tạo visual rhythm.

---

# 44. Component philosophy

Ưu tiên:

```text
List
Row
Section
Divider
Inline action
Bottom sheet
```

trước:

```text
Card
```

Ví dụ Conversation list:

```text
Avatar | Name
       | Last message       Time
```

Không cần:

```text
┌───────────────────────┐
│  Avatar  Name         │
│          Message      │
│                       │
└───────────────────────┘
```

cho mọi row.

---

# 45. Design System

```text
DESIGN SYSTEM

Colors
├── Semantic colors
├── Surface
└── Content

Typography
├── Display
├── Headline
├── Title
├── Body
├── Label
└── Caption

Spacing
├── 4
├── 8
├── 12
├── 16
├── 20
├── 24
└── 32

Shape
├── Small
├── Medium
├── Large
└── Full

Components
├── Buttons
├── Inputs
├── Search
├── Avatar
├── Badge
├── List row
├── Tabs
├── Bottom navigation
├── Bottom sheet
├── Dialog
├── Chat bubble
├── Reaction
├── Notification row
├── Presence indicator
└── Media preview

States
├── Loading
├── Empty
├── Error
├── Offline
└── Disabled
```

---

# 46. Android-specific UX

## Back

Back hierarchy phải predictable:

```text
Conversation
 ← Chats

Group Info
 ← Group Chat

User Profile
 ← Previous context
```

Nếu đang:

```text
Search → Profile → Message
```

back phải quay lại context hợp lý.

Không phải lúc nào cũng:

```text
Profile → Discover
```

một cách cứng nhắc.

---

## Keyboard

Trong Chat:

```text
Keyboard open
↓
Composer stays above keyboard
↓
Latest message remains visible
```

Khi gửi:

```text
Input clear
↓
Keyboard remains open
```

không đóng keyboard sau mỗi message.

---

## Bottom sheet

Dùng cho:

```text
Message actions
Attachments
Member actions
Conversation actions
Reaction
```

Không dùng dialog cho mọi thứ.

---

## Dialog

Chỉ dùng khi:

```text
Destructive
Irreversible
High consequence
```

Ví dụ:

```text
Delete conversation?
Leave group?
Transfer admin?
Block user?
```

---

# 47. Deep links

Architecture nên hỗ trợ:

```text
Push notification
↓
Target screen
```

Ví dụ:

```text
notification://message/{id}
notification://profile/{id}
notification://group/{id}
```

Không nhất thiết phải dùng đúng format trên về mặt implementation, nhưng UX phải được thiết kế theo tư duy deep-link.

---

# 48. Những gì nên GIỮ từ proposal cũ

Giữ:

### 1. Chats là primary surface

Đúng.

### 2. Group nằm trong hệ thống conversation

Giữ concept này và **không tách Group thành tab**.

### 3. Nearby là feature nổi bật

Giữ.

Nhưng chuyển:

```text
Nearby
```

từ primary navigation thành:

```text
Discover → Nearby
```

### 4. Contextual message actions

Giữ.

Đây là cách tốt nhất để xử lý message actions.

### 5. Progressive disclosure

Giữ hoàn toàn.

---

# 49. Những gì nên BỎ

## Bỏ `Groups` tab

Vì Group là conversation type.

## Bỏ `More`

Nếu có thể.

## Bỏ việc đưa AI thành navigation

AI là utility.

## Bỏ việc đưa Voice Biometrics thành feature chính

Nó là security/identity capability.

## Bỏ việc thiết kế mỗi feature thành một screen

Một số feature nên là:

```text
Bottom sheet
Dialog
Inline state
Contextual action
```

---

# 50. Những gì nên THÊM

Không phải thêm feature để "app xịn hơn", mà thêm UX infrastructure.

## 1. Global search behavior

Cần xác định:

```text
Search people
Search conversations
Search messages
```

## 2. Offline states

Rất quan trọng với messaging.

## 3. Deep-link behavior

Đặc biệt notification → message/profile/group.

## 4. Permission education

Location/microphone/camera.

## 5. Undo

Ví dụ:

```text
Conversation archived
              [Undo]
```

hoặc:

```text
Message deleted
              [Undo]
```

nếu architecture cho phép.

## 6. Consistent presence model

Online/offline/last seen phải dùng cùng một semantic system.

## 7. Context preservation

Profile mở từ Nearby phải back về Nearby.

Profile mở từ Chat phải back về Chat.

---

# 51. Phase Planning

Không nên build 13 cụm cùng lúc.

## Phase 1 — Communication Core

```text
Auth
Profile
Chats
1-1 messaging
Text
Image
File
Read receipt
Typing
Unread
Conversation management
```

Mục tiêu:

> App đã thực sự chat được.

---

## Phase 2 — Group

```text
Create group
Members
Admin
Group profile
Group message
Group typing
Group read state
```

---

## Phase 3 — Social

```text
Follow
Followers
Following
Friends
Suggested users
Profile discovery
Block
```

---

## Phase 4 — Activity

```text
Notification Center
Follow notification
Reaction
Mention
Group events
Push notification
Deep links
```

---

## Phase 5 — Nearby

```text
Location permission
Nearby toggle
3 km discovery
Map
Distance
Location privacy
Expiration
Realtime location
```

Nearby nên làm **sau khi Social model ổn định**, vì Nearby phụ thuộc rất nhiều vào user relationship + privacy + block.

---

## Phase 6 — Rich Communication

```text
Voice message
Video call
Media viewer
Advanced forwarding
```

---

## Phase 7 — AI

```text
Smart Reply
AI Assistant
```

---

## Phase 8 — Advanced Identity

```text
Voice Biometrics
```

Đây là phase sau vì complexity/security requirement cao hơn nhiều so với messaging core.

---

# 52. Recommended Final UI/UX Architecture

Đây là architecture mình thực sự khuyến nghị triển khai:

```text
┌──────────────────────────────────────────┐
│                  APP                     │
│                                          │
│  ┌────────┐ ┌──────────┐ ┌────────────┐ │
│  │ Chats  │ │ Discover │ │Notifications│ │
│  └────────┘ └──────────┘ └────────────┘ │
│                         ┌──────────────┐ │
│                         │   Profile    │ │
│                         └──────────────┘ │
└──────────────────────────────────────────┘
```

## Chats

```text
Chats
│
├── Search
├── Pinned
├── All
│   ├── Direct
│   └── Groups
│
├── New message
└── New group
```

## Conversation

```text
Conversation
│
├── Messages
├── Reply
├── Reaction
├── Forward
├── Edit
├── Delete
├── Revoke
├── Attachments
├── Voice
├── Call
├── Video
├── Search
├── Shared Media
└── Conversation Info
```

## Discover

```text
Discover
│
├── Search People
├── Suggested
├── Nearby
├── Friends
├── Followers
└── Following
```

## Notifications

```text
Notifications
│
├── Follow
├── Reaction
├── Mention
├── Group
├── Friend
└── System
```

## Profile

```text
Profile
│
├── Identity
├── Followers / Following / Friends
├── Edit Profile
│
└── Settings
    ├── Account
    ├── Privacy
    ├── Presence
    ├── Notifications
    ├── Chat
    ├── Nearby
    ├── Security
    └── Blocked Users
```

## Global

```text
GLOBAL
│
├── Search
├── Push
├── Deep links
├── AI Assistant
├── Presence
├── Permissions
└── Offline state
```

---

# 53. Final mental model

Nếu phải tóm gọn toàn bộ app thành 4 câu mà người dùng có thể hiểu:

```text
CHAT
→ Tôi nói chuyện với ai?

DISCOVER
→ Tôi tìm ai?

NOTIFICATIONS
→ Có chuyện gì mới?

PROFILE
→ Tôi là ai và tôi kiểm soát app thế nào?
```

Đây là mental model mình muốn architecture cuối cùng xoay quanh.

Không phải:

```text
Chat
Group
Nearby
AI
Voice
Settings
...
```

---

# 54. Final recommendation

### Primary Navigation

```text
💬 Chats
🔎 Discover
🔔 Notifications
👤 Profile
```

### Không đưa vào primary navigation

```text
Groups
Nearby
AI
Calls
Friends
Settings
Blocked Users
Voice Biometrics
```

### Đặt chúng vào context phù hợp

```text
Groups
→ Chats

Nearby
→ Discover

Friends / Followers / Following
→ Discover

Calls
→ Conversation

AI Smart Reply
→ Conversation

AI Assistant
→ Global utility

Voice Biometrics
→ Settings / Security

Blocked Users
→ Settings / Privacy

Notification settings
→ Profile / Settings
```

### Core hierarchy

```text
                    APP
                     │
        ┌────────────┼────────────┐
        ↓            ↓            ↓
     CHAT         PEOPLE       ACTIVITY
        │            │            │
   Conversation   Discover    Notifications
        │            │
    Message       Nearby
    Group         Friends
    Media         Follow
    Calls         Profile
        │
        └────────────┐
                     ↓
                  PROFILE
                     │
                  SETTINGS
```

---

# 55. Kết luận quan trọng nhất

Với feature list hiện tại, mình **không khuyên bạn tiếp tục tư duy "mỗi module = một tab/một màn hình lớn"**.

Architecture tốt hơn là:

> **Conversation-centric + People-centric + Activity-centric + Profile-centric**

Trong đó:

```text
Chats       = Communication
Discover    = People + Discovery
Notifications = Activity
Profile     = Identity + Settings
```

và mọi feature nâng cao được đặt **theo context sử dụng**, thay vì theo tên module.

Đặc biệt, mình sẽ chốt 5 quyết định kiến trúc sau:

1. **Group không phải primary tab.**
2. **Nearby không phải primary tab.**
3. **More bị loại bỏ nếu có thể.**
4. **AI/Calls/Voice Biometrics là contextual/utility features.**
5. **Chats là trung tâm của communication system, còn Discover là trung tâm của people/discovery system.**

Nếu triển khai theo architecture này, sau này bạn vẫn có thể thêm:

```text
Stories
Channels
Communities
Events
Marketplace
Public Groups
AI Agents
```

mà không nhất thiết phải phá bottom navigation hiện tại.

Đó mới là dấu hiệu của một **Information Architecture có khả năng scale**, thay vì một UI chỉ vừa đủ cho feature list hiện tại.

**Tóm lại:** mình đồng ý với hướng sửa từ `Chats / Groups / Nearby / More` sang **`Chats / Discover / Notifications / Profile`**, nhưng mình sẽ đi xa hơn một bước: xây toàn bộ hệ thống xoay quanh **Conversation, People, Activity và Identity**, đồng thời coi Nearby, AI, Calls, Voice Biometrics và các action nâng cao là các capability nằm trong context phù hợp chứ không phải destination độc lập. 
