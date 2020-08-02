package it.polito.ai.backend.services.exercise;

import it.polito.ai.backend.dtos.AssignmentDTO;
import it.polito.ai.backend.dtos.CourseDTO;
import it.polito.ai.backend.dtos.ExerciseDTO;
import it.polito.ai.backend.dtos.StudentDTO;
import it.polito.ai.backend.entities.*;
import it.polito.ai.backend.repositories.*;
import it.polito.ai.backend.services.team.CourseNotEnabledException;
import it.polito.ai.backend.services.team.CourseNotFoundException;
import it.polito.ai.backend.services.team.StudentNotEnrolledException;
import it.polito.ai.backend.services.team.StudentNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import it.polito.ai.backend.services.Utils;


@Service
@Transactional
public class ExerciseServiceImpl implements ExerciseService {
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    ExerciseRepository exerciseRepository;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    TeacherRepository teacherRepository;
    @Autowired
    AssignmentRepository assignmentRepository;
    @Autowired
    ModelMapper modelMapper;


    @Override
    @PreAuthorize("hasRole('TEACHER') and @securityServiceImpl.isTaught(#courseId)")
    public void addExerciseForCourse(String courseId, Timestamp published, Timestamp expired, MultipartFile file) throws IOException {
        Optional<Course> course = courseRepository.findById(courseId);

            if (!course.isPresent()) {
                throw new CourseNotFoundException(courseId);

            } else if (!course.get().isEnabled()) {
                throw new CourseNotEnabledException(courseId);
            }
            System.out.println(expired);
            if(expired.before(Utils.getNow())){
                throw new ExerciseServiceException("Invalid expired time");}

            Exercise exercise = new Exercise();
            exercise.setPublished(published);
            exercise.setExpired(expired);
            exercise.setCourse(course.get());
            exercise.setImage(Utils.getBytes(file));
            exerciseRepository.save(exercise);

    }

    @Override
    public List<AssignmentDTO> getAssignmentByStudentAndExercise(String studentId, Long exerciseId) {
        Optional<Student> student = studentRepository.findById(studentId);
        if(!student.isPresent())
            throw new StudentNotFoundException(studentId);
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if(!exercise.isPresent())
            throw new ExerciseNotFoundException(exerciseId.toString());

        return assignmentRepository.findByStudentAndAndExercise(student.get(),exercise.get())
                .stream()
                .sorted(Comparator.comparing(Assignment::getPublished,Timestamp::compareTo))
                .map(a -> modelMapper.map(a,AssignmentDTO.class))
                .collect(Collectors.toList());
    }


    @Override
    public Optional<ExerciseDTO> getExercise(Long id) {
        return exerciseRepository.findById(id)
                .map(e -> modelMapper.map(e, ExerciseDTO.class));
    }

    @Override
    public Optional<AssignmentDTO> getAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .map(a -> modelMapper.map(a, AssignmentDTO.class));

    }

    @Override
    public Optional<ExerciseDTO> getExerciseForAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .map(a -> modelMapper.map(a.getExercise(), ExerciseDTO.class));

    }


    @Override
    @PreAuthorize("(hasRole('TEACHER') and @securityServiceImpl.isTaught(#courseId) ) or " +
            "(hasRole('STUDENT') and @securityServiceImpl.isEnrolled(#courseId))")
    public List<ExerciseDTO> getExercisesForCourse(String courseId) {
        Optional<Course> course = courseRepository.findById(courseId);
        if(!course.isPresent()){
            throw new CourseNotFoundException(courseId);
        }
        return course.get().getExercises().stream()
                .map(e -> modelMapper.map(e,ExerciseDTO.class ))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CourseDTO> getCourse(Long exerciseId) {
        return exerciseRepository.findById(exerciseId)
                .map(e -> modelMapper.map(e.getCourse(), CourseDTO.class));
    }

    @Override
    public boolean setAssignmentsNullForExercise(Long exerciseId) {
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if(!exercise.isPresent())
            throw  new ExerciseNotFoundException(exerciseId.toString());
         /* Non devono esistere altri assigment*/
       List<Assignment> assignment = exercise.get().getAssignments();
       if(!assignment.isEmpty())
          return false;
       // Per ogni studente iscritto al corso aggiungere un'elaborato con stato null
       List<Student> students= exercise.get().getCourse().getStudents();
        for (Student student:students) {
            addAssignmentByte(
                    Utils.getNow(),
                    AssignmentStatus.NULL,
                    true,null,exercise.get().getImage(),student.getId(),exerciseId);
        }
        return  true;
    }

    @Override
    public boolean setAssignmentsReadForStudentAndExercise(Long exerciseId, String studentId) {
        Optional<Student> student = studentRepository.findById(studentId);
        if(!student.isPresent())
            throw new StudentNotFoundException(studentId);
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if(!exercise.isPresent())
            throw new ExerciseNotFoundException(exerciseId.toString());
        Assignment assignment = assignmentRepository.findByStudentAndAndExercise(student.get(),exercise.get())
                .stream()
                .sorted(Comparator.comparing(Assignment::getPublished,Timestamp::compareTo))
                .reduce((a1,a2)-> a2).orElse(null);

        if(assignment==null)
            throw  new AssignmentNotFoundException(studentId);

        Byte[] image = assignment.getImage();
        if(assignment.getStatus()==AssignmentStatus.NULL ||
                (assignment.getStatus()==AssignmentStatus.RIVSTO && assignment.isFlag())) {
            addAssignmentByte(Utils.getNow(), AssignmentStatus.LETTO,true,null,image,studentId,exerciseId);
            return  true;
        }


        else return false;
    }

    @Override
    public boolean checkAssignment(Long exerciseId, String studentId){
        /*Lo studente può caricare solo una soluzione prima che il docente gli dia il permesso per rifralo*/
        Optional<Student> student = studentRepository.findById(studentId);
        if(!student.isPresent())
            throw new StudentNotFoundException(studentId);
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if(!exercise.isPresent())
            throw new ExerciseNotFoundException(exerciseId.toString());
        Assignment assignment = assignmentRepository.findByStudentAndAndExercise(student.get(),exercise.get())
                .stream()
                .sorted(Comparator.comparing(Assignment::getPublished,Timestamp::compareTo))
                .reduce((a1,a2)-> a2).orElse(null);
        if(assignment==null)
            throw  new AssignmentNotFoundException(studentId);
        return exercise.get().getExpired().after(Utils.getNow()) && assignment.isFlag() && assignment.getStatus() == AssignmentStatus.LETTO;

    }

    @Override
    public Optional<StudentDTO> getStudentForAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
               .map(a -> modelMapper.map(a.getStudent(), StudentDTO.class));
    }

    @Override
    public List<AssignmentDTO> getLastAssignments(Long exerciseId) {
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if(!exercise.isPresent())
            throw  new ExerciseNotFoundException(exerciseId.toString());
        Course course = exercise.get().getCourse();
        List<Student> students = course.getStudents();
        List<Assignment> lastAssignments = new ArrayList<Assignment>();

        for (Student student:students) {
            Assignment lastAssignment = assignmentRepository.findByStudent(student)
                    .stream()
                    .sorted(Comparator.comparing(Assignment::getPublished,Timestamp::compareTo))
                    .reduce((a1,a2)-> a2).orElse(null);
            if(lastAssignment==null)
                throw  new AssignmentNotFoundException(student.getId());
            lastAssignments.add(lastAssignment);
        }


        return lastAssignments.stream()
                .map(a -> modelMapper.map(a, AssignmentDTO.class))
                .collect(Collectors.toList());

    }



    @Override
    public AssignmentDTO addAssignmentByte(Timestamp published, AssignmentStatus state, boolean flag, Integer score, Byte[] image, String studentId, Long exerciseId) {
        Optional<Student> student = studentRepository.findById(studentId);
        if(!student.isPresent())
            throw  new StudentNotFoundException(studentId);
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if(!exercise.isPresent())
            throw  new ExerciseNotFoundException(exerciseId.toString());

        if(!student.get().getCourses().contains(exercise.get().getCourse()))
            throw  new StudentNotEnrolledException(studentId);

        Assignment assignment = new Assignment();
        assignment.setScore(score);
        assignment.setFlag(flag);
        assignment.setStatus(state);
        assignment.setExercise(exercise.get());
        assignment.setPublished(published);
        assignment.setStudent(student.get());
        assignment.setImage(image);
        assignmentRepository.save(assignment);
        return modelMapper.map(assignment,AssignmentDTO.class);

    }




}

