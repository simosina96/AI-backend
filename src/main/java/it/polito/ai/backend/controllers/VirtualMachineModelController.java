package it.polito.ai.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import it.polito.ai.backend.dtos.CourseDTO;
import it.polito.ai.backend.dtos.VirtualMachineModelDTO;
import it.polito.ai.backend.services.vm.VirtualMachineModelNotFoundException;
import it.polito.ai.backend.services.vm.VirtualMachineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@RestController
@RequestMapping("/API/virtual-machine-models")
@Validated
public class VirtualMachineModelController {

    @Autowired
    VirtualMachineService virtualMachineService;

    @Operation(summary = "get virtual machine model")
    @GetMapping("/{modelId}")
    VirtualMachineModelDTO getOne(@PathVariable @NotNull Long modelId) {
        VirtualMachineModelDTO virtualMachineModelDTO = virtualMachineService.getVirtualMachineModel(modelId)
                .orElseThrow(() -> new VirtualMachineModelNotFoundException(modelId.toString()));
        String courseId = virtualMachineService.getCourseForVirtualMachineModel(modelId).map(CourseDTO::getId).orElse(null);
        return ModelHelper.enrich(virtualMachineModelDTO, courseId);
    }

    @Operation(summary = "create a new virtual machine model")
    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    VirtualMachineModelDTO addVirtualMachineModel(@RequestBody @Valid VirtualMachineModelDTO virtualMachineModelDTO) {
        String courseId = virtualMachineModelDTO.getCourseId();
        VirtualMachineModelDTO virtualMachineModel = virtualMachineService.createVirtualMachineModel(courseId, virtualMachineModelDTO);
        return ModelHelper.enrich(virtualMachineModel, courseId);
    }

    @Operation(summary = "delete an existing virtual machine model")
    @DeleteMapping("/{modelId}")
    void deleteVirtualMachineModel(@PathVariable @NotNull Long modelId) {
        if (!virtualMachineService.deleteVirtualMachineModel(modelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "please turn off all the virtual machines using this model");
        }
    }
}
