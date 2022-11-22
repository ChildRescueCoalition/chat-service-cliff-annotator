package org.mediacloud.cliff.servlet.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.mediacloud.cliff.extractor.EntityExtractor;

import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParseTextRequest {
    @NotBlank
    public String externalId;
    @NotBlank
    public String text;

    public String language = EntityExtractor.ENGLISH;
}
