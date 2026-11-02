# Kết quả kiểm thử – HRM JWT Login

Kiểm tra ngày 02/11/2026 theo giờ Nhật Bản. Java 17.0.20, Maven Wrapper 3.9.9, Spring Boot 3.5.16, JJWT 0.9.1.

## Build và kiểm thử tích hợp

Kết quả thực tế của Maven:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Môi trường tạo bài cần khai báo proxy riêng cho Maven để tải dependency. Cấu hình proxy nằm ngoài source nộp bài; không có thay đổi proxy/mạng trong project. Sau khi cấu hình mạng của máy phù hợp, lệnh chuẩn là `./mvnw clean verify`.

Các test dùng AuthenticationManager, BCrypt, JwtProvider và SecurityFilterChain thật; không dùng mock hoặc `@WithMockUser`.

| Nhóm kiểm tra | Kết quả |
| --- | --- |
| Đúng username/password → JSON đúng ba trường | Đạt |
| Token có chữ ký HS256, sub=admin, role=ADMIN | Đạt |
| exp − iat = 86.400.000 mili giây (1 ngày) | Đạt |
| Sai password hoặc username không tồn tại → 401, không có token | Đạt |
| Thiếu trường, trường rỗng/null, JSON sai, thiếu body → 400 | Đạt |
| Bearer token hợp lệ → đúng 3 nhân viên của bài trước | Đạt |
| Thiếu token hoặc dùng Basic Auth → 401 | Đạt |
| Không tạo session, đăng nhập không tự xác thực request kế tiếp | Đạt |
| Token lỗi/rỗng/hết hạn/sai key/bị sửa/không có chữ ký → 401 | Đạt |
| Token thiếu exp, role sai kiểu, thuật toán ngoài HS256 → 401 | Đạt |
| Vẫn đăng nhập được khi header mang token cũ không hợp lệ | Đạt |

## Kiểm tra ứng dụng chạy thật

Chạy JAR trên cổng thử nghiệm 18081 rồi gọi HTTP trực tiếp. Không chỉ dùng MockMvc. Ứng dụng đã được dừng sau khi kiểm tra.

| Thao tác | HTTP thực tế |
| --- | --- |
| GET /api/v1/employees, không có token | 401 |
| POST /login, sai password | 401 |
| POST /login, admin / Admin123! | 200 |
| GET /api/v1/employees, Bearer token vừa cấp | 200, đúng 3 nhân viên |
| GET /api/v1/employees, token lỗi | 401 |
| POST /login, body {} | 400 |
| POST /login, đúng tài khoản kèm token cũ lỗi | 200 |
| GET /api/v1/employees, không token sau khi đăng nhập | 401 |
| GET /api/v1/employees, token đã hết hạn | 401 |

Ngoài 9 request HTTP trên, chữ ký của token thật được kiểm tra độc lập bằng Python HMAC-SHA256, xác nhận sub/role và thời hạn 86.400 giây. Tổng cộng 10 kiểm tra ứng dụng chạy thật đều đạt. Không lưu token thật vào source nộp bài.

## Kiểm tra source và tài liệu

- Employee.java, EmployeeController.java, class Application và ba file Maven Wrapper được đối chiếu byte với ZIP bài trước: giữ nguyên.
- POM XML hợp lệ và khai báo đúng io.jsonwebtoken:jjwt:0.9.1.
- Postman Collection có 6 request; JSON và cú pháp các script JavaScript đã được kiểm tra. Không chạy giao diện Postman trong môi trường tạo bài; các HTTP tương ứng đã được gọi trực tiếp như trên.
- Biến accessToken trong collection để trống.
- ZIP nộp source không chứa target, JAR build, cấu hình proxy, log hoặc thông tin xác thực cá nhân.

Secret và password mặc định trong application.properties là dữ liệu demo được ghi rõ trong README. Khi sử dụng ngoài bài tập, cấu hình secret/password riêng qua biến môi trường.
