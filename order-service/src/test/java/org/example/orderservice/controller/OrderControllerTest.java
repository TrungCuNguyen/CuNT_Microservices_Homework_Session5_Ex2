package org.example.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.orderservice.constant.OrderConstant;
import org.example.orderservice.dto.request.OrderRequestDTO;
import org.example.orderservice.dto.response.OrderResponseDTO;
import org.example.orderservice.dto.response.ProductClientResponseDTO;
import org.example.orderservice.exception.BadRequestException;
import org.example.orderservice.exception.GlobalExceptionHandler;
import org.example.orderservice.exception.InternalServerErrorException;
import org.example.orderservice.exception.ResourceNotFoundException;
import org.example.orderservice.exception.ServiceUnavailableException;
import org.example.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OrderController.class, GlobalExceptionHandler.class})
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private OrderService orderService;

    @Test
    void testCreateOrder_Success() throws Exception {
        OrderRequestDTO request = OrderRequestDTO.builder()
                .customerId(1L)
                .productId(2L)
                .quantity(3)
                .build();

        OrderResponseDTO response = OrderResponseDTO.builder()
                .id(10L)
                .customerId(1L)
                .productId(2L)
                .orderDate(LocalDateTime.now())
                .totalAmount(450.0)
                .build();

        when(orderService.createOrder(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.customerId").value(1L))
                .andExpect(jsonPath("$.productId").value(2L))
                .andExpect(jsonPath("$.totalAmount").value(450.0));
    }

    @Test
    void testCreateOrder_QuantityLessThanOrEqualToZero_Returns400() throws Exception {
        OrderRequestDTO request = OrderRequestDTO.builder()
                .customerId(1L)
                .productId(2L)
                .quantity(0)
                .build();

        when(orderService.createOrder(any()))
                .thenThrow(new BadRequestException(OrderConstant.INVALID_QUANTITY));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(OrderConstant.INVALID_QUANTITY))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testCreateOrder_DatabaseSaveFailure_Returns500() throws Exception {
        OrderRequestDTO request = OrderRequestDTO.builder()
                .customerId(1L)
                .productId(2L)
                .quantity(2)
                .build();

        when(orderService.createOrder(any()))
                .thenThrow(new InternalServerErrorException(OrderConstant.DB_SAVE_ERROR));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value(OrderConstant.DB_SAVE_ERROR))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetOrderById_NotFound() throws Exception {
        when(orderService.getOrderById(99L))
                .thenThrow(new ResourceNotFoundException(String.format(OrderConstant.ORDER_NOT_FOUND, 99L)));

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Đơn hàng với ID 99 không tồn tại!"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetProductFromProductService_Success() throws Exception {
        ProductClientResponseDTO product = ProductClientResponseDTO.builder()
                .id(1L)
                .name("Iphone 17")
                .price(123456.0)
                .stockQuantity(50)
                .build();

        when(orderService.getProductFromProductService(1L)).thenReturn(product);

        mockMvc.perform(get("/api/v1/orders/getProduct/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Iphone 17"))
                .andExpect(jsonPath("$.price").value(123456.0))
                .andExpect(jsonPath("$.stockQuantity").value(50));
    }

    @Test
    void testGetProductFromProductService_ServiceUnavailable_Returns503() throws Exception {
        when(orderService.getProductFromProductService(1L))
                .thenThrow(new ServiceUnavailableException("PRODUCT-SERVICE hiện không khả dụng (Không tìm thấy instance nào)"));

        mockMvc.perform(get("/api/v1/orders/getProduct/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("PRODUCT-SERVICE hiện không khả dụng (Không tìm thấy instance nào)"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
