package org.mediacloud.cliff.servlet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RawParseTextResponse {
    private String externalId;
    private HashMap<?, ?> result;
}
