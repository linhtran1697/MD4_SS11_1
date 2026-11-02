package com.example.hrm;

import com.example.hrm.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.admin-password=Admin123!",
        "jwt.secret=integration-test-signing-key-with-at-least-sixty-four-bytes-for-hs512-tests",
        "jwt.expiration=86400000"
})
@AutoConfigureMockMvc
class JwtLoginIntegrationTest {

    private static final String EMPLOYEES = "/api/v1/employees";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Value("${jwt.secret}")
    private String secret;

    @Test
    void validCredentialsReturnRequiredJsonAndSignedOneDayClaims() throws Exception {
        MvcResult result = login("admin", "Admin123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.size()).isEqualTo(3);
        String token = json.get("accessToken").asText();
        assertThat(token.split("\\.")).hasSize(3);
        Claims claims = jwtProvider.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(86400000L);
        assertThat(Jwts.parser().setSigningKey(key()).parseClaimsJws(token).getHeader().getAlgorithm())
                .isEqualTo("HS256");
    }

    @Test
    void wrongPasswordReturns401WithoutToken() throws Exception {
        login("admin", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không đúng"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void unknownUsernameReturns401WithoutToken() throws Exception {
        login("unknown", "Admin123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void emptyBodyFieldsReturn400() throws Exception {
        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void whitespaceUsernameReturns400() throws Exception {
        login("   ", "Admin123!").andExpect(status().isBadRequest());
    }

    @Test
    void nullPasswordReturns400() throws Exception {
        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validBearerTokenReturnsTheOriginalThreeEmployees() throws Exception {
        mockMvc.perform(get(EMPLOYEES).header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$[0].salary").value(18000000))
                .andExpect(jsonPath("$[1].fullName").value("Trần Thị Bình"))
                .andExpect(jsonPath("$[2].salary").value(25000000));
    }

    @Test
    void noTokenReturns401AndDoesNotLeakEmployeeData() throws Exception {
        mockMvc.perform(get(EMPLOYEES))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.fullName").doesNotExist())
                .andExpect(jsonPath("$.salary").doesNotExist());
    }

    @Test
    void loginDoesNotCreateASessionForLaterRequests() throws Exception {
        MvcResult result = login("admin", "Admin123!").andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mockMvc.perform(get(EMPLOYEES)).andExpect(status().isUnauthorized());
    }

    @Test
    void oldBasicCredentialsCannotBypassBearerAuthentication() throws Exception {
        mockMvc.perform(get(EMPLOYEES).with(httpBasic("admin", "Admin123!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        assertRejected("not-a-jwt");
    }

    @Test
    void emptyBearerTokenReturns401() throws Exception {
        assertRejected("");
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String token = Jwts.builder().setSubject("admin").claim("role", "ADMIN")
                .setIssuedAt(new Date(System.currentTimeMillis() - 120000))
                .setExpiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(SignatureAlgorithm.HS256, key()).compact();
        assertRejected(token);
    }

    @Test
    void differentSigningKeyReturns401() throws Exception {
        String token = standardBuilder()
                .signWith(SignatureAlgorithm.HS256,
                        "a-completely-different-signing-key-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))
                .compact();
        assertRejected(token);
    }

    @Test
    void tamperingWithPayloadReturns401() throws Exception {
        String[] parts = loginToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("admin", "intruder").getBytes(StandardCharsets.UTF_8));
        assertRejected(String.join(".", parts));
    }

    @Test
    void unsignedTokenReturns401() throws Exception {
        assertRejected(standardBuilder().compact());
    }

    @Test
    void tokenWithoutExpirationReturns401() throws Exception {
        assertRejected(Jwts.builder().setSubject("admin").claim("role", "ADMIN")
                .setIssuedAt(new Date()).signWith(SignatureAlgorithm.HS256, key()).compact());
    }

    @Test
    void tokenWithWrongRoleTypeReturns401() throws Exception {
        assertRejected(standardBuilder().claim("role", 123)
                .signWith(SignatureAlgorithm.HS256, key()).compact());
    }

    @Test
    void unexpectedSigningAlgorithmReturns401() throws Exception {
        assertRejected(standardBuilder().signWith(SignatureAlgorithm.HS512, key()).compact());
    }

    @Test
    void loginIsUsableWithAnOldInvalidBearerHeader() throws Exception {
        mockMvc.perform(post("/login").header(HttpHeaders.AUTHORIZATION, "Bearer old.invalid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password)
            throws Exception {
        var body = objectMapper.createObjectNode().put("username", username).put("password", password);
        return mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private String loginToken() throws Exception {
        String body = login("admin", "Admin123!").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private byte[] key() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private io.jsonwebtoken.JwtBuilder standardBuilder() {
        return Jwts.builder().setSubject("admin").claim("role", "ADMIN")
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60000));
    }

    private void assertRejected(String token) throws Exception {
        mockMvc.perform(get(EMPLOYEES).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
