package org.egov.filestore.domain.model;

import lombok.*;

import java.awt.image.BufferedImage;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

@AllArgsConstructor
@Getter
@Builder
@Setter
public class Artifact {
    private MultipartFile multipartFile;
    
    private FileLocation fileLocation;
    
    private String fileContentInString;
}

