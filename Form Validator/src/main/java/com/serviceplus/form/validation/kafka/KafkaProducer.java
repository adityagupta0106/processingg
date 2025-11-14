package com.serviceplus.form.validation.kafka;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class KafkaProducer {

	private static final Logger KafkaLogger = LogManager.getLogger("KafkaLogger");
	
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
    
    public Mono<Boolean> sendMessage(String topic,String key, String message) {
    	try {
    		KafkaLogger.info("Sending message to topic {} with key {} and message {}",topic,key,message);
    		kafkaTemplate.send(topic, key, message);
    		return Mono.just(true);
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    		KafkaLogger.info("[1]Unable to send message for topic {} with key {} with error {}",topic,key,e.getMessage());
    		return Mono.just(false);
    	}
    }

}
