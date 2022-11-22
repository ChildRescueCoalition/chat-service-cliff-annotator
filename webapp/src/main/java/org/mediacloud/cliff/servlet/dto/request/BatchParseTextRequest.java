package org.mediacloud.cliff.servlet.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import java.util.Collection;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchParseTextRequest {
    @NotEmpty
    public Collection<ParseTextRequest> requests;
}
