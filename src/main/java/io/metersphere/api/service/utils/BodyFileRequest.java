package io.metersphere.api.service.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BodyFileRequest {
    private String reportId;
    private String testId;
}
