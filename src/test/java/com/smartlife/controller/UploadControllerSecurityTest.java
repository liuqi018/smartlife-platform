package com.smartlife.controller;

import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UploadControllerSecurityTest {
    private UploadController controller;

    @BeforeEach void setUp() {
        controller = new UploadController();
        UserDTO user = new UserDTO(); user.setId(7L); UserHolder.saveUser(user);
    }
    @AfterEach void tearDown() { UserHolder.removeUser(); }

    @Test void rejectsIllegalExtension() {
        Result result = controller.uploadImage(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1}));
        assertFalse(result.getSuccess());
    }
    @Test void rejectsOversizedImage() {
        Result result = controller.uploadImage(new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]));
        assertFalse(result.getSuccess());
    }
    @Test void rejectsPathTraversalBeforeFileAccess() {
        assertFalse(controller.deleteBlogImg("../../application.yaml").getSuccess());
    }
}
