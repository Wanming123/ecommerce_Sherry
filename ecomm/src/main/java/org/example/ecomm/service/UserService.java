package org.example.ecomm.service;

import org.example.ecomm.dto.UserDto;
import org.example.ecomm.dto.request.CreateUserRequest;
import org.example.ecomm.dto.request.UserUpdateRequest;
import org.example.ecomm.pojo.User;

public interface UserService {
    User getUserById(Long userId);
    User createUser(CreateUserRequest request);
    User updateUser(UserUpdateRequest request, Long userId);
    void deleteUser(Long userId);

    UserDto convertUserToDto(User user);
    User getAuthenticatedUser();
}
