package org.example.userservice.service;

import org.example.userservice.dto.AddressDto;
import org.example.userservice.dto.AddressRequest;

import java.util.List;

public interface AddressService {
    AddressDto addAddress(Long userId, AddressRequest request);
    List<AddressDto> getAddressesByUser(Long userId);
    AddressDto updateAddress(Long addressId, AddressRequest request);
    void deleteAddress(Long addressId);
    AddressDto setDefaultAddress(Long userId, Long addressId);
}
