package org.example.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.constant.OrderConstant;
import org.example.orderservice.dto.request.OrderRequestDTO;
import org.example.orderservice.dto.response.OrderResponseDTO;
import org.example.orderservice.dto.response.ProductClientResponseDTO;
import org.example.orderservice.entity.Order;
import org.example.orderservice.exception.BadRequestException;
import org.example.orderservice.exception.InternalServerErrorException;
import org.example.orderservice.exception.ResourceNotFoundException;
import org.example.orderservice.exception.ServiceUnavailableException;
import org.example.orderservice.repository.OrderRepository;
import org.example.orderservice.service.OrderService;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;

    @Override
    public ProductClientResponseDTO getProductFromProductService(Long productId) {
        // BƯỚC A: Hỏi Eureka lấy danh sách các máy chủ đang chạy ProductService
        // Lưu ý: Tên service lấy trong file application.properties
        List<ServiceInstance> instances = discoveryClient.getInstances("product-service");
        if (instances == null || instances.isEmpty()) {
            instances = discoveryClient.getInstances("PRODUCT-SERVICE");
        }

        // BƯỚC B: Xử lý lỗi (Theo yêu cầu bài tập: Trả về 503 nếu rỗng)
        if (instances == null || instances.isEmpty()) {
            throw new ServiceUnavailableException("PRODUCT-SERVICE hiện không khả dụng (Không tìm thấy instance nào)");
        }

        // BƯỚC C: Lấy Instance đầu tiên trong danh sách (Tương lai bạn có thể viết logic random để cân bằng tải)
        ServiceInstance productInstance = instances.get(0);

        // BƯỚC D: Lấy URL gốc (Ví dụ: http://localhost:8082 hoặc http://localhost:9090) từ instance
        String baseUrl = productInstance.getUri().toString();

        // BƯỚC E: Nối chuỗi để tạo ra API URL hoàn chỉnh (Ví dụ: http://localhost:9090/api/v1/products/123)
        String targetUrl = baseUrl + "/api/v1/products/" + productId;

        // BƯỚC F: Thực hiện gọi API lấy về sản phẩm
        try {
            ProductClientResponseDTO product = restClient.get()
                    .uri(targetUrl)
                    .retrieve()
                    .body(ProductClientResponseDTO.class);

            if (product == null) {
                throw new ResourceNotFoundException("Không tìm thấy sản phẩm với ID " + productId);
            }
            return product;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi kết nối tới PRODUCT-SERVICE tại {}: {}", targetUrl, e.getMessage());
            throw new ServiceUnavailableException("PRODUCT-SERVICE hiện không khả dụng (Không thể kết nối)");
        }
    }

    @Override
    public OrderResponseDTO createOrder(OrderRequestDTO requestDTO) {
        if (requestDTO.getQuantity() == null || requestDTO.getQuantity() <= 0) {
            throw new BadRequestException(OrderConstant.INVALID_QUANTITY);
        }

        // Lấy thông tin sản phẩm và giá qua DiscoveryClient
        ProductClientResponseDTO product = getProductFromProductService(requestDTO.getProductId());
        if (product.getPrice() == null) {
            throw new BadRequestException("Giá sản phẩm không hợp lệ!");
        }

        Double totalAmount = product.getPrice() * requestDTO.getQuantity();

        try {
            Order order = Order.builder()
                    .customerId(requestDTO.getCustomerId())
                    .productId(requestDTO.getProductId())
                    .orderDate(LocalDateTime.now())
                    .totalAmount(totalAmount)
                    .build();

            Order saved = orderRepository.save(order);
            return mapToResponse(saved);
        } catch (Exception e) {
            log.error("Lỗi khi lưu đơn hàng vào DB: {}", e.getMessage(), e);
            throw new InternalServerErrorException(OrderConstant.DB_SAVE_ERROR + ": " + e.getMessage());
        }
    }

    @Override
    public OrderResponseDTO getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(OrderConstant.ORDER_NOT_FOUND, id)));
        return mapToResponse(order);
    }

    @Override
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private OrderResponseDTO mapToResponse(Order order) {
        return OrderResponseDTO.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .productId(order.getProductId())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .build();
    }
}
