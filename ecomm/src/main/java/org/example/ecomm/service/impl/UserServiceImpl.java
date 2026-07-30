package org.example.ecomm.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ecomm.dto.UserDto;
import org.example.ecomm.dto.request.CreateUserRequest;
import org.example.ecomm.dto.request.UserUpdateRequest;
import org.example.ecomm.exception.AlreadyExistException;
import org.example.ecomm.exception.ResourceNotFoundException;
import org.example.ecomm.pojo.User;
import org.example.ecomm.repository.UserRepository;
import org.example.ecomm.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found!"));
    }

    @Override
    public User createUser(CreateUserRequest request) {
        return  Optional.of(request)
                .filter(user -> !userRepository.existsByEmail(request.getEmail()))
                .map(req -> {
                    User user = new User();
                    user.setEmail(request.getEmail());
                    user.setPassword(request.getPassword());
                    user.setFirstName(request.getFirstName());
                    user.setLastName(request.getLastName());
                    return  userRepository.save(user);
                }) .orElseThrow(() -> new AlreadyExistException("Oops!" +request.getEmail() +" already exists!"));
    }

    @Override
    public User updateUser(UserUpdateRequest request, Long userId) {
        return  userRepository.findById(userId).map(existingUser ->{
            existingUser.setFirstName(request.getFirstName());
            existingUser.setLastName(request.getLastName());
            return userRepository.save(existingUser);
        }).orElseThrow(() -> new ResourceNotFoundException("User not found!"));

    }

    @Override
    public void deleteUser(Long userId) {
        userRepository.findById(userId).ifPresentOrElse(userRepository :: delete, () ->{
            throw new ResourceNotFoundException("User not found!");
        });
    }

    @Override
    public UserDto convertUserToDto(User user) {
        return modelMapper.map(user, UserDto.class);
    }

    @Override
    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email);
    }
}
