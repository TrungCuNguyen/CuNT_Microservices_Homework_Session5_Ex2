# Microservices Project (Session 4)

Dự án gồm **Discovery Server** và **3 Microservices** đã được tích hợp Eureka Registry:
- **`discovery-server`** (Cổng: **8761**, Eureka Server - "Cuốn danh bạ trung tâm")
- **`customer-service`** (Cổng: **8081**, Database: `customer_db`, Eureka Client)
- **`product-service`** (Cổng: **8082** hoặc linh hoạt ví dụ **9090**, Database: `product_db`, Eureka Client)
- **`order-service`** (Cổng: **8083**, Database: `order_db`, Eureka Client, DiscoveryClient)

---

## 1. Cơ chế Dynamic Discovery trong Order-Service

Trong `order-service`, thay vì fix cứng địa chỉ `http://localhost:8082`, dịch vụ sử dụng **`DiscoveryClient`** để truy vấn Eureka: *"Các instance của `product-service` đang chạy ở địa chỉ/port nào?"*:

```java
public ProductClientResponseDTO getProductFromProductService(Long productId) {
    // Bước A: Hỏi Eureka lấy danh sách các instance của PRODUCT-SERVICE
    List<ServiceInstance> instances = discoveryClient.getInstances("product-service");
    if (instances == null || instances.isEmpty()) {
        instances = discoveryClient.getInstances("PRODUCT-SERVICE");
    }

    // Bước B: Xử lý lỗi - Nếu không tìm thấy instance nào -> throw 503 Service Unavailable
    if (instances == null || instances.isEmpty()) {
        throw new ServiceUnavailableException("PRODUCT-SERVICE hiện không khả dụng (Không tìm thấy instance nào)");
    }

    // Bước C: Lấy Instance đầu tiên
    ServiceInstance productInstance = instances.get(0);

    // Bước D: Lấy baseUrl động từ Eureka (VD: http://localhost:8082 hoặc http://localhost:9090)
    String baseUrl = productInstance.getUri().toString();

    // Bước E: Nối chuỗi URL
    String targetUrl = baseUrl + "/api/v1/products/" + productId;

    // Bước F: Gọi API lấy về thông tin sản phẩm
    return restClient.get()
            .uri(targetUrl)
            .retrieve()
            .body(ProductClientResponseDTO.class);
}
```

---

## 2. API Kiểm thử trên Postman

### 2.1. Lấy thông tin Product thông qua Order Service:
- **Method**: `GET`
- **URL**: `http://localhost:8083/api/v1/orders/getProduct/1`

#### Khi Product Service đang hoạt động (kể cả khi đổi port sang `9090`):
- **Response (200 OK)**:
```json
{
  "id": 1,
  "name": "Iphone 17",
  "price": 123456.0,
  "stockQuantity": 50
}
```

#### Khi Product Service bị tắt hoặc Eureka chưa có instance:
- **Response (503 Service Unavailable)**:
```json
{
  "timestamp": "2026-10-13T22:35:00",
  "status": 503,
  "error": "Service Unavailable",
  "message": "PRODUCT-SERVICE hiện không khả dụng (Không tìm thấy instance nào)"
}
```

---

## 3. Thứ tự khởi động

1. **Khởi động Discovery Server (Eureka):**
   ```bash
   ./gradlew :discovery-server:bootRun
   ```
2. **Khởi động các Microservices:**
   ```bash
   ./gradlew :customer-service:bootRun
   ./gradlew :product-service:bootRun
   ./gradlew :order-service:bootRun
   ```
3. **Kiểm tra Eureka Dashboard:** Truy cập `http://localhost:8761`.
