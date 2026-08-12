package org.example.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.userservice.dto.AddressDto;
import org.example.userservice.dto.AddressRequest;
import org.example.userservice.dto.ApiResponse;
import org.example.userservice.exception.ResourceNotFoundException;
import org.example.userservice.service.AddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/addresses")
public class AddressController {
    private final AddressService addressService;

    @PostMapping("/user/{userId}/add")
    public ResponseEntity<ApiResponse> addAddress(@PathVariable Long userId, @RequestBody AddressRequest request) {
        try {
            AddressDto address = addressService.addAddress(userId, request);
            return ResponseEntity.ok(new ApiResponse("Add address success!", address));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getAddresses(@PathVariable Long userId) {
        List<AddressDto> addresses = addressService.getAddressesByUser(userId);
        return ResponseEntity.ok(new ApiResponse("Success", addresses));
    }

    @PutMapping("/{addressId}/update")
    public ResponseEntity<ApiResponse> updateAddress(@PathVariable Long addressId, @RequestBody AddressRequest request) {
        try {
            AddressDto address = addressService.updateAddress(addressId, request);
            return ResponseEntity.ok(new ApiResponse("Update address success!", address));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @DeleteMapping("/{addressId}/delete")
    public ResponseEntity<ApiResponse> deleteAddress(@PathVariable Long addressId) {
        try {
            addressService.deleteAddress(addressId);
            return ResponseEntity.ok(new ApiResponse("Delete address success!", null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PutMapping("/user/{userId}/{addressId}/set-default")
    public ResponseEntity<ApiResponse> setDefaultAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        try {
            AddressDto address = addressService.setDefaultAddress(userId, addressId);
            return ResponseEntity.ok(new ApiResponse("Default address updated!", address));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }
}
