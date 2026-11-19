package ua.nure.msa.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Точка входу order-service: власник стану замовлення та учасник саги. */
@SpringBootApplication(scanBasePackages = "ua.nure.msa")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
