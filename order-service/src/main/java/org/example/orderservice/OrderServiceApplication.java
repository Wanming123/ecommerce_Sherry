package org.example.orderservice;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.example.orderservice.config.CheckoutConcurrencyConfig;
import org.modelmapper.ModelMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    // The default RestTemplate is backed by HttpURLConnection, which keep-alive-caches only
    // ~5 connections per destination (JDK default) - far below the inventory-reservation
    // executor's pool size, so parallel fan-out would just queue up waiting for sockets instead
    // of actually running concurrently. Pooled at the same size as that executor so the two
    // limits line up.
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(CheckoutConcurrencyConfig.MAX_POOL_SIZE);
        connectionManager.setDefaultMaxPerRoute(CheckoutConcurrencyConfig.MAX_POOL_SIZE);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}
