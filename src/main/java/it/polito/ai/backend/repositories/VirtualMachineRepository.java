package it.polito.ai.backend.repositories;

import it.polito.ai.backend.entities.VirtualMachine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualMachineRepository extends JpaRepository<VirtualMachine, Long> {
}
