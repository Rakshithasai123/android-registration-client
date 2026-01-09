/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration_client.api_services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.mosip.registration.clientmanager.constant.AuditEvent;
import io.mosip.registration.clientmanager.constant.Components;
import io.mosip.registration.clientmanager.dto.registration.RegistrationDto;
import io.mosip.registration.clientmanager.dto.uispec.FieldSpecDto;
import io.mosip.registration.clientmanager.service.TemplateService;
import io.mosip.registration.clientmanager.spi.AuditManagerService;
import io.mosip.registration.clientmanager.spi.RegistrationService;
import io.mosip.registration.clientmanager.util.UserInterfaceHelperService;
import io.mosip.registration.packetmanager.util.JsonUtils;
import io.mosip.registration_client.model.RegistrationDataPigeon;

@Singleton
public class RegistrationApi implements RegistrationDataPigeon.RegistrationDataApi {
    private final RegistrationService registrationService;
    RegistrationDto registrationDto;
    TemplateService templateService;
    AuditManagerService auditManagerService;

    @Inject
    public RegistrationApi(RegistrationService registrationService, TemplateService templateService,
                           AuditManagerService auditManagerService) {
        this.registrationService = registrationService;
        this.templateService = templateService;
        this.auditManagerService = auditManagerService;
    }

    @Override
    public void startRegistration(@NonNull List<String> languages, @NonNull String flowType, @NonNull String process, @NonNull RegistrationDataPigeon.Result<String> result) {
        Log.d("RegistrationApi", "startRegistration called with process: '" + process + "'");
        Log.d("RegistrationApi", "startRegistration called with flowType: '" + flowType + "'");

        String flowTypeUpper = flowType.toUpperCase().trim();
        Log.d("RegistrationApi", "FlowType uppercase: '" + flowTypeUpper + "'");

        switch (flowTypeUpper) {
            case "NEW":
                Log.d("RegistrationApi", "Matched NEW - auditing NAV_NEW_REG");
                auditManagerService.audit(AuditEvent.NAV_NEW_REG, Components.REGISTRATION);
                break;
            case "LOST":
                Log.d("RegistrationApi", "Matched LOST - auditing NAV_LOST_UIN");
                auditManagerService.audit(AuditEvent.NAV_LOST_UIN, Components.REGISTRATION);
                break;
            case "UPDATE":
                Log.d("RegistrationApi", "Matched UPDATE - auditing NAV_UIN_UPDATE");
                auditManagerService.audit(AuditEvent.NAV_UIN_UPDATE, Components.REGISTRATION);
                break;
            case "CORRECTION":
                Log.d("RegistrationApi", "Matched CORRECTION - auditing NAV_CORRECTION");
                auditManagerService.audit(AuditEvent.NAV_CORRECTION, Components.REGISTRATION);
                break;
            default:
                Log.w("RegistrationApi", "NO MATCH for process: '" + process + "'");
                break;
        }
        String response = "";
        try {
            this.registrationDto = registrationService.startRegistration(languages, flowType, process);
        } catch (Exception e) {
            response = e.getMessage();
            Log.e(getClass().getSimpleName(), "Registration start failed", e);
        }
        result.success(response);
    }

    @Override
    public void evaluateMVELVisible(@NonNull String fieldData, @NonNull RegistrationDataPigeon.Result<Boolean> result) {
        try {
            FieldSpecDto fieldSpecDto = JsonUtils.jsonStringToJavaObject(fieldData, new TypeReference<FieldSpecDto>() {
            });
            this.registrationDto = this.registrationService.getRegistrationDto();
            boolean isFieldVisible = UserInterfaceHelperService.isFieldVisible(fieldSpecDto, this.registrationDto.getMVELDataContext());
            result.success(isFieldVisible);
            return;
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Object Mapping error: " + Arrays.toString(e.getStackTrace()));
        }
        result.success(false);
    }

    @Override
    public void evaluateMVELRequired(@NonNull String fieldData, @NonNull RegistrationDataPigeon.Result<Boolean> result) {
        try {
            FieldSpecDto fieldSpecDto = JsonUtils.jsonStringToJavaObject(fieldData, new TypeReference<FieldSpecDto>() {
            });
            this.registrationDto = this.registrationService.getRegistrationDto();
            boolean isFieldRequired = UserInterfaceHelperService.isRequiredField(fieldSpecDto, this.registrationDto.getMVELDataContext());
            result.success(isFieldRequired);
            return;
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Object Mapping error: " + Arrays.toString(e.getStackTrace()));
        }
        result.success(false);
    }

    @Override
    public void getPreviewTemplate(@NonNull Boolean isPreview, @NonNull Map<String, String> templateTitleValues, @NonNull RegistrationDataPigeon.Result<String> result) {
        String template = "";
        try {
            this.registrationDto = this.registrationService.getRegistrationDto();
            template = this.templateService.getTemplate(this.registrationDto, isPreview, templateTitleValues);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Fetch template failed: ", e);
        }
        result.success(template);
    }

    @Override
    public void submitRegistrationDto(@NonNull String makerName, @NonNull RegistrationDataPigeon.Result<RegistrationDataPigeon.RegistrationSubmitResponse> result) {
        auditManagerService.audit(AuditEvent.REG_DEMO_NEXT, Components.REGISTRATION);
        String response = "";
        String errorCode = "";
        try {
            RegistrationDto registrationDto = this.registrationService.getRegistrationDto();
            if (registrationDto.getAdditionalInfoRequestId() != null) {
                response = registrationDto.getAdditionalInfoRequestId().split("-")[0];
            } else {
                response = registrationDto.getRId();
            }
            registrationService.submitRegistrationDto(makerName);
        } catch (Exception e) {
            errorCode = e.getMessage();
            Log.i("RegistrationApi", "Registration submission failed: " + errorCode);
            auditManagerService.audit(AuditEvent.CREATE_PACKET_FAILED, Components.REGISTRATION, errorCode);
            Log.e(getClass().getSimpleName(), "Failed on registration submission", e);
        }
        RegistrationDataPigeon.RegistrationSubmitResponse registrationSubmitResponse =
                new RegistrationDataPigeon.RegistrationSubmitResponse
                        .Builder()
                        .setRId(response)
                        .setErrorCode(errorCode)
                        .build();
        result.success(registrationSubmitResponse);
    }

    @Override
    public void setApplicationId(@NonNull String applicationId, @NonNull RegistrationDataPigeon.Result<Void> result) {
        try {
            this.registrationDto.setApplicationId(applicationId);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Set application ID failed: " + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void setAdditionalReqId(@NonNull String additionalReqId, @NonNull RegistrationDataPigeon.Result<Void> result) {
        try {
            this.registrationDto.setAdditionalInfoRequestId(additionalReqId);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Set additional request ID failed: " + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void setMachineLocation(@NonNull Double latitude, @NonNull Double longitude, @NonNull RegistrationDataPigeon.Result<Void> result) {
        auditManagerService.audit(AuditEvent.NAV_GEO_LOCATION, Components.REGISTRATION);
        try {
            this.registrationDto.setGeoLocation(longitude, latitude);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(),"Set machine location failed:" + Arrays.toString(e.getStackTrace()));
        }
    }
}

