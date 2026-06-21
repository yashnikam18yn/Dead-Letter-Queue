package com.dlqanalyzer.dlq_analyzer.controller;

import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import com.dlqanalyzer.dlq_analyzer.repository.DlqMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dlqanalyzer.dlq_analyzer.service.ReplayEngine;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final DlqMessageRepository dlqMessageRepository;
    private final ReplayEngine replayEngine;

    //get all message
    @GetMapping
    public List<DlqMessage> getAllMessage(){
        return dlqMessageRepository.findAll();
    }

    //get message by id
    @GetMapping("/{id}")
    public ResponseEntity<DlqMessage> getById(@PathVariable String id) {
        return dlqMessageRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    //GET message by status
    @GetMapping("/status/{status}")
    public List<DlqMessage> getByStatus(@PathVariable MessageStatus status){
        return dlqMessageRepository.findByStatus(status);
    }

    //discard/ delete message
    //discard/ delete message
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> discard(@PathVariable String id){
        if (!dlqMessageRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        replayEngine.discardMessage(id, "admin");
        return ResponseEntity.ok().build();
    }

}