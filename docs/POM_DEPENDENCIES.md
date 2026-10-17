# Dependency gợi ý (Spring Initializr: Maven, Java 17, Spring Boot 3.3.x)

Chọn khi tạo project: **Spring Web, Thymeleaf, Spring Data JPA, MS SQL Server Driver, Spring Security,
Validation, Lombok, Spring Boot DevTools**. Thêm thủ công vào `pom.xml`:

```xml
<dependency>
  <groupId>nz.net.ultraq.thymeleaf</groupId>
  <artifactId>thymeleaf-layout-dialect</artifactId>
</dependency>
<dependency>
  <groupId>org.thymeleaf.extras</groupId>
  <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
<!-- Test -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

FE lib nạp qua CDN trong `layout/base.html` (hoặc WebJars): Bootstrap 5, Bootstrap Icons, Swiper, AOS, SweetAlert2, Chart.js.
Test DB: dùng SQL Server thật (Docker `mcr.microsoft.com/mssql/server:2022-latest`) – tránh H2 vì CHECK/filtered index khác SQL Server.
