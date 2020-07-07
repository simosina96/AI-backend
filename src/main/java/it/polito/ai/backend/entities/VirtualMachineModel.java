package it.polito.ai.backend.entities;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

@Entity
@Data
public class VirtualMachineModel {

    @Id
    @GeneratedValue
    Long id;
    int min_vcpu;
    int max_vcpu;
    int min_disk;
    int max_disk;
    int min_ram;
    int max_ram;

    @OneToOne(mappedBy = "vm_model")
    Course course;
}
