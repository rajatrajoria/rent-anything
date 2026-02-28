package com.rajat.rent_anything.user.records.request;

import com.rajat.rent_anything.user.enums.TrustStatus;
import lombok.NonNull;

public record UpdateTrustStatusRequest(
        @NonNull TrustStatus status
) { }
