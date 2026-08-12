package org.example.userservice.service;

import lombok.RequiredArgsConstructor;
import org.example.userservice.dto.AddressDto;
import org.example.userservice.dto.AddressRequest;
import org.example.userservice.exception.ResourceNotFoundException;
import org.example.userservice.pojo.Address;
import org.example.userservice.pojo.User;
import org.example.userservice.repository.AddressRepository;
import org.example.userservice.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public AddressDto addAddress(Long userId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        Address address = new Address();
        applyRequest(address, request);
        address.setUser(user);

        boolean isFirstAddress = addressRepository.findByUserId(userId).isEmpty();
        if (isFirstAddress) {
            address.setDefault(true);
        } else if (request.isDefault()) {
            clearExistingDefault(userId);
        }

        return convertToDto(addressRepository.save(address));
    }

    @Override
    public List<AddressDto> getAddressesByUser(Long userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(this::convertToDto)
                .toList();
    }

    @Transactional
    @Override
    public AddressDto updateAddress(Long addressId, AddressRequest request) {
        Address address = getAddressOrThrow(addressId);
        applyRequest(address, request);

        if (request.isDefault()) {
            clearExistingDefault(address.getUser().getId());
            address.setDefault(true);
        }

        return convertToDto(addressRepository.save(address));
    }

    @Override
    public void deleteAddress(Long addressId) {
        Address address = getAddressOrThrow(addressId);
        addressRepository.delete(address);
    }

    @Transactional
    @Override
    public AddressDto setDefaultAddress(Long userId, Long addressId) {
        Address address = getAddressOrThrow(addressId);
        if (!address.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Address does not belong to this user");
        }
        clearExistingDefault(userId);
        address.setDefault(true);
        return convertToDto(addressRepository.save(address));
    }

    private Address getAddressOrThrow(Long addressId) {
        return addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found!"));
    }

    private void clearExistingDefault(Long userId) {
        addressRepository.findByUserId(userId).stream()
                .filter(Address::isDefault)
                .forEach(existing -> {
                    existing.setDefault(false);
                    addressRepository.save(existing);
                });
    }

    private void applyRequest(Address address, AddressRequest request) {
        address.setName(request.getName());
        address.setLocality(request.getLocality());
        address.setAddress(request.getAddress());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPinCode(request.getPinCode());
        address.setMobile(request.getMobile());
    }

    private AddressDto convertToDto(Address address) {
        return modelMapper.map(address, AddressDto.class);
    }
}
