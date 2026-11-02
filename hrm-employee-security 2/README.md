# HRM – Đăng nhập và trả về JWT

Kế thừa project `hrm-employee-security` của bài trước: giữ model `Employee`, API `GET /api/v1/employees`, package `com.example.hrm`, Maven Wrapper và Spring Boot 3.5.16. Bổ sung đăng nhập JSON qua `AuthenticationManager`, trả JWT và xác thực các request tiếp theo bằng Bearer token. Dữ liệu nằm trong bộ nhớ, không cần MySQL.

## 1. Nội dung đã triển khai

- `POST /login`: nhận username/password, xác thực thành công rồi mới tạo token.
- `JwtProvider`: đọc secret và thời hạn từ `application.properties`; ký bằng HS256.
- Payload có `sub` (username), `role`, `iat` và `exp`; mặc định hết hạn sau 1 ngày.
- Response đăng nhập có đúng ba trường: `accessToken`, `type`, `username`.
- `JwtAuthenticationFilter`: kiểm tra chữ ký, hạn dùng và các claim bắt buộc trước khi cho truy cập API nhân viên.
- Sai tài khoản hoặc token không hợp lệ → `401`; thiếu dữ liệu/JSON sai → `400`.
- Spring Security chạy stateless; mỗi request đến API bảo vệ phải gửi token.

## 2. Công nghệ và dependency

Java 17 hoặc 21, Maven, Spring Web, Spring Security, Bean Validation và **JJWT 0.9.1**. Project giữ Maven như bài trước, nên dòng Gradle trong đề:

```groovy
implementation 'io.jsonwebtoken:jjwt:0.9.1'
```

được khai báo tương đương trong `pom.xml`:

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.1</version>
</dependency>
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
    <version>2.3.1</version>
</dependency>
```

JAXB được thêm để đáp ứng việc JJWT 0.9.1 gọi `javax.xml.bind.DatatypeConverter` trên Java 17. Giữ phiên bản JJWT theo yêu cầu bài tập; khi phát triển sản phẩm thực tế, xem hướng dẫn nâng cấp chính thức.

## 3. Các file chính

| File trong `src/main/java/com/example/hrm` | Vai trò |
| --- | --- |
| `HrmEmployeeSecurityApplication.java` | Chạy Spring Boot |
| `model/Employee.java` | Model bài trước: id, fullName, salary |
| `controller/EmployeeController.java` | Danh sách 3 nhân viên cố định từ bài trước |
| `controller/AuthController.java` | POST /login, gọi AuthenticationManager rồi JwtProvider |
| `config/SecurityConfig.java` | Tài khoản demo, BCrypt, AuthenticationManager, filter chain |
| `dto/LoginRequest.java`, `dto/LoginResponse.java` | Dữ liệu đăng nhập và JSON trả token |
| `security/JwtProvider.java` | Tạo và xác minh JWT |
| `security/JwtAuthenticationFilter.java` | Đọc Bearer token cho request tiếp theo |
| `security/RestAuthenticationEntryPoint.java` | JSON 401 khi thiếu/sai token |
| `exception/GlobalExceptionHandler.java` | Lỗi đăng nhập và validation |

## 4. Chạy ứng dụng

### IntelliJ IDEA trên macOS

1. Giải nén, mở thư mục **`hrm-employee-security`** có `pom.xml`.
2. Chọn JDK 17 hoặc 21 tại **Project Structure → Project SDK**.
3. Reload Maven và chờ tải dependency; lần chạy đầu cần Internet.
4. Mở `HrmEmployeeSecurityApplication.java`, bấm **Run**.
5. Giữ ứng dụng chạy trên cổng `8080` khi thử Postman.

### Terminal

macOS/Linux, tại thư mục có `pom.xml`:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Tài khoản demo dùng cho bài này:

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `Admin123!` | `ADMIN` |

Password được BCrypt mã hóa trước khi lưu vào `InMemoryUserDetailsManager`. Bài này đã có tài khoản cấu hình riêng, nên không dùng mật khẩu ngẫu nhiên trong Console của bài Spring Security mặc định.

## 5. Đăng nhập trên Postman

**Method:** `POST`  
**URL:** `http://localhost:8080/login`  
**Authorization:** `No Auth`  
**Body:** `raw` → `JSON`

```json
{
  "username": "admin",
  "password": "Admin123!"
}
```

Kết quả **200 OK**:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "username": "admin"
}
```

Token trong ví dụ đã rút gọn. Khi thử API tiếp theo, phải sao chép **toàn bộ** `accessToken` thực tế vừa nhận.

Payload minh họa sau khi giải mã:

```json
{
  "sub": "admin",
  "role": "ADMIN",
  "iat": 1793616000,
  "exp": 1793702400
}
```

`iat`/`exp` trong JWT là Unix time tính bằng giây; `jwt.expiration` trong cấu hình tính bằng mili giây. Hai thời điểm minh họa cách nhau 86.400 giây. Username được lấy từ `Authentication.getName()`, role từ authority của tài khoản đã xác thực (`ROLE_ADMIN` → `ADMIN`), không lấy role từ body do client gửi.

JWT được ký, payload không được mã hóa: không đưa mật khẩu hoặc dữ liệu nhạy cảm vào claim.

## 6. Dùng token gọi danh sách nhân viên

**Method:** `GET`  
**URL:** `http://localhost:8080/api/v1/employees`

Tab **Authorization** → **Bearer Token** → dán token vào ô **Token** → **Send**. Trong ô Token chỉ dán chuỗi JWT; Postman tự thêm tiền tố `Bearer`.

Header tương ứng:

```http
Authorization: Bearer <accessToken>
```

Kết quả **200 OK**, giữ nguyên dữ liệu bài trước:

```json
[
  { "id": 1, "fullName": "Nguyễn Văn An", "salary": 18000000 },
  { "id": 2, "fullName": "Trần Thị Bình", "salary": 22000000 },
  { "id": 3, "fullName": "Lê Văn Cường", "salary": 25000000 }
]
```

Thiếu token, token hết hạn, sai chữ ký hoặc bị sửa → **401 Unauthorized**. Khi token hết hạn, gọi lại `/login` để nhận token mới. Project này chưa triển khai refresh token.

## 7. Postman Collection có sẵn

Import `postman/HRM_JWT_Login.postman_collection.json`. Các biến `baseUrl`, `username`, `password` đã có giá trị demo; `accessToken` được để trống khi nộp.

1. Chạy **01 - Login**: script tự lưu token vào biến collection `accessToken`.
2. Chạy **02 - Employees with JWT**: tự sử dụng token vừa lưu.
3. Các request còn lại thử thiếu token, sai password, thiếu dữ liệu và token lỗi.

Collection có script kiểm tra response. Xem kết quả trong **Test Results** của Postman; kết quả dự kiến được ghi trong tên request. Khi đổi password bằng biến môi trường, cập nhật biến `password` tương ứng trong collection.

## 8. Gọi bằng cURL

Đăng nhập:

```bash
curl -i -X POST http://localhost:8080/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin123!"}'
```

Thay `<accessToken>` bằng token thật:

```bash
curl -i http://localhost:8080/api/v1/employees \
  -H 'Authorization: Bearer <accessToken>'
```

## 9. Cấu hình

Trong `src/main/resources/application.properties`:

```properties
jwt.secret=${JWT_SECRET:hrm-session-11-demo-key-only-change-before-deployment-2026}
jwt.expiration=${JWT_EXPIRATION:86400000}
app.security.admin-password=${ADMIN_PASSWORD:Admin123!}
```

Giá trị mặc định chỉ dành cho bài tập. Khi sử dụng ngoài bài tập, đặt `JWT_SECRET` là chuỗi bí mật ngẫu nhiên ít nhất 32 byte và đặt `ADMIN_PASSWORD` riêng qua biến môi trường; không commit giá trị thật lên GitHub. Ví dụ tạo secret: `openssl rand -base64 48`. Ứng dụng đọc giá trị cấu hình thành byte UTF-8 nhất quán khi ký và xác minh.

Không cấu hình secret vào Postman: client chỉ cần username/password để đăng nhập và token để gọi API. Nếu đổi secret, token đã cấp bằng secret cũ sẽ không xác minh được.

## 10. Kiểm thử và đóng gói

```bash
./mvnw clean verify
```

Trên Windows: `.\mvnw.cmd clean verify`.

`JwtLoginIntegrationTest` kiểm tra luồng xác thực thật, ký/xác minh JWT thật và filter chain; không dùng `@WithMockUser` hoặc mock AuthenticationManager. Có 22 ca: đăng nhập đúng/sai, validation, thời hạn một ngày, truy cập nhân viên, không tạo session, token bị sửa/sai key/hết hạn/không có chữ ký/thiếu claim và từ chối Basic Auth.

Kết quả kiểm tra thực tế ghi tại [docs/KET_QUA_KIEM_THU.md](docs/KET_QUA_KIEM_THU.md).

Sau khi build thành công:

```bash
java -jar target/hrm-employee-security-0.0.1-SNAPSHOT.jar
```

## 11. Nộp lên GitHub

Nếu đang dùng repository Session 10, chép source cập nhật vào repository đó rồi commit/push theo remote đã có. Model nhân viên, controller nhân viên và class chạy ứng dụng được giữ nguyên.

Nếu nộp bằng repository mới, tạo repository trống rồi chạy tại thư mục project; thay URL mẫu bằng repository của mình:

```bash
git init
git add .
git commit -m "Add JWT login and Bearer authentication to HRM"
git branch -M main
git remote add origin https://github.com/TEN_GITHUB/hrm-jwt-login.git
git push -u origin main
```

Đẩy **source đã giải nén**, gồm `src`, `pom.xml`, Maven Wrapper, README, docs và Postman. `.gitignore` đã loại `target`, IDE, log và `.env`.

## 12. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
| --- | --- |
| /login trả 401 | Kiểm tra `admin` / `Admin123!`, hoặc password đã đặt bằng ADMIN_PASSWORD |
| /login trả 400 | Chọn Body → raw → JSON, gửi đủ hai trường không rỗng |
| API nhân viên trả 401 | Chọn Bearer Token, dán đầy đủ token mới nhận, kiểm tra hạn dùng |
| Dùng Basic Auth vẫn bị 401 | Bài này dùng /login rồi Bearer token |
| Lỗi javax/xml/bind/DatatypeConverter | Reload Maven và kiểm tra dependency jaxb-api:2.3.1 |
| Maven không tải được dependency | Kiểm tra mạng/proxy của máy và Reload Maven |
| Port 8080 already in use | Dừng ứng dụng cũ hoặc đổi server.port và baseUrl |
| Permission denied khi chạy wrapper | Chạy `chmod +x mvnw` |

Tham khảo: [Spring Security – AuthenticationManager cho REST login](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/index.html#publish-authentication-manager-bean), [mã nguồn JJWT](https://github.com/jwtk/jjwt), [JJWT 0.9.1 và JAXB trên Java 11+](https://github.com/jwtk/jjwt/issues/401).
