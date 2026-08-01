package org.example.ecomm.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ecomm.dto.ImageDto;
import org.example.ecomm.exception.ResourceNotFoundException;
import org.example.ecomm.pojo.Image;
import org.example.ecomm.pojo.Product;
import org.example.ecomm.repository.ImageRepository;
import org.example.ecomm.service.ImageService;
import org.example.ecomm.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.rowset.serial.SerialBlob;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {
    private final ImageRepository imageRepository;
    private final ProductService productService;
    private final ExecutorService imageUploadExecutor;

    @Override
    public Image getImageById(Long id) {
        return imageRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("no image found with id: " + id));
    }

    @Override
    public void deleteImageById(Long id) {
        imageRepository.findById(id).ifPresentOrElse(imageRepository::delete, () -> {
            throw new ResourceNotFoundException("no image found with id: " + id);
        });
    }

//    @Override
//    public List<ImageDto> saveImages(List<MultipartFile> files, Long productId) {
//        Product product = productService.getProductById(productId);
//
//        List<ImageDto> savedImageDtos = new ArrayList<>();
//        for (MultipartFile file: files) {
//            try {
//                Image image = new Image();
//                image.setFileName(file.getOriginalFilename());
//                image.setFileType(file.getContentType());
//                image.setImage(new SerialBlob(file.getBytes()));
//                image.setProduct(product);
//
//                String buildDownloadUrl = "/api/v1/images/image/download/";
//                String downloadUrl = buildDownloadUrl + image.getId();
//                image.setDownloadUrl(downloadUrl);
//                Image savedImage = imageRepository.save(image);
//                savedImage.setDownloadUrl(buildDownloadUrl + savedImage.getId());
//                imageRepository.save(savedImage);
//
//                ImageDto imageDto = new ImageDto();
//                imageDto.setId(savedImage.getId());
//                imageDto.setFileName(savedImage.getFileName());
//                imageDto.setDownloadUrl(savedImage.getDownloadUrl());
//                savedImageDtos.add(imageDto);
//
//            } catch (IOException | SQLException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        return savedImageDtos;
//    }
    @Override
    public List<ImageDto> saveImages(List<MultipartFile> files, Long productId) {
        Product product = productService.getProductById(productId);

        List<CompletableFuture<ImageDto>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(
                        () -> saveOneImage(file, product), imageUploadExecutor))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }


    private ImageDto saveOneImage(MultipartFile file, Product product) {
        try {
            Image image = new Image();
            image.setFileName(file.getOriginalFilename());
            image.setFileType(file.getContentType());
            image.setImage(new SerialBlob(file.getBytes()));
            image.setProduct(product);

            Image savedImage = imageRepository.save(image);
            savedImage.setDownloadUrl("/api/v1/images/image/download/" + savedImage.getId());
            imageRepository.save(savedImage);

            ImageDto imageDto = new ImageDto();
            imageDto.setId(savedImage.getId());
            imageDto.setFileName(savedImage.getFileName());
            imageDto.setDownloadUrl(savedImage.getDownloadUrl());
            return imageDto;
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateImage(MultipartFile file, Long imageId) {
        Image image = getImageById(imageId);
        try {
            image.setFileName(file.getOriginalFilename());
            image.setImage(new SerialBlob(file.getBytes()));
            imageRepository.save(image);
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
