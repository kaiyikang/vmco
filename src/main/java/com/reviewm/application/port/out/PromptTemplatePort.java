package com.reviewm.application.port.out;

import com.reviewm.domain.model.PromptPackage;
import com.reviewm.domain.model.PromptProfile;

public interface PromptTemplatePort {
    String render(PromptPackage promptPackage, PromptProfile profile);
}
