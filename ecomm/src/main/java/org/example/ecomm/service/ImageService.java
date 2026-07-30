package org.example.ecomm.service;

import org.example.ecomm.dto.ImageDto;
import org.example.ecomm.pojo.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageService {
    Image getImageById(Long id);
    void deleteImageById(Long id);
    List<ImageDto>  saveImages(List<MultipartFile> files, Long productId);
    void updateImage(MultipartFile file, Long imageId);
}
