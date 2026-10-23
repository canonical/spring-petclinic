/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.system;

import java.time.Duration;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("observability-demo")
class SlowDemoController {

	private static final Logger log = LoggerFactory.getLogger(SlowDemoController.class);

	private final ObservationRegistry registry;

	private final Duration delay;

	SlowDemoController(ObservationRegistry registry, @Value("${petclinic.demo.slow-delay:2s}") Duration delay) {
		if (delay.isNegative() || delay.compareTo(Duration.ofSeconds(5)) > 0) {
			throw new IllegalArgumentException("Slow endpoint demo delay must be between 0 and 5 seconds");
		}
		this.registry = registry;
		this.delay = delay;
	}

	@GetMapping("/demo/slow")
	Map<String, Object> slow() throws InterruptedException {
		Observation operation = Observation.start("demo.slow-operation", this.registry);
		try (Observation.Scope scope = operation.openScope()) {
			Thread.sleep(this.delay.toMillis());
			log.info("slow demo operation completed delay_ms={}", this.delay.toMillis());
			return Map.of("operation", "demo.slow-operation", "delay_ms", this.delay.toMillis());
		}
		catch (InterruptedException ex) {
			operation.error(ex);
			log.warn("slow demo operation interrupted", ex);
			Thread.currentThread().interrupt();
			throw ex;
		}
		finally {
			operation.stop();
		}
	}

}
