package org.example.ecomm.service;

import org.example.ecomm.dto.ProductDto;
import org.example.ecomm.dto.request.AddProductRequest;
import org.example.ecomm.dto.request.ProductUpdateRequest;
import org.example.ecomm.pojo.Product;

import java.util.List;

public interface ProductService {
    Product addProduct(AddProductRequest request);

    Product getProductById(Long id);
    void deleteProductById(Long id);
    Product updateProduct(ProductUpdateRequest request, Long productId);
    List<Product> getAllProducts();
    List<Product> getProductByCategory(String category);
    List<Product> getProductByBrand(String brand);
    List<Product> getProductByCategoryAndBrand(String category, String brand);
    List<Product> getProductByName(String name);
    List<Product> getProductByBrandAndName(String brand, String name);

    Long countProductsByBrandAndName(String brand, String name);

    List<ProductDto> getConvertedProducts(List<Product> products);
    ProductDto convertToDto(Product product);

    void decreaseInventory(Long productId, int quantity);
}
