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
import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlowDemoControllerTests {

	private final ObservationRegistry registry = ObservationRegistry.create();

	private final List<Observation.Context> completed = new ArrayList<>();

	@BeforeEach
	void captureObservations() {
		this.registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStop(Observation.Context context) {
				completed.add(context);
			}
		});
	}

	@Test
	void delaysWithinNamedChildObservation() throws Exception {
		Observation parent = Observation.start("http.server.requests", this.registry);
		try (Observation.Scope scope = parent.openScope()) {
			long started = System.nanoTime();
			var result = new SlowDemoController(this.registry, Duration.ofMillis(20)).slow();
			assertThat(System.nanoTime() - started).isGreaterThanOrEqualTo(Duration.ofMillis(20).toNanos());
			assertThat(result).containsEntry("operation", "demo.slow-operation").containsEntry("delay_ms", 20L);
			assertThat(this.completed).hasSize(1);
			assertThat(this.completed.get(0).getName()).isEqualTo("demo.slow-operation");
			assertThat(this.completed.get(0).getParentObservation()).isSameAs(parent);
			assertThat(this.registry.getCurrentObservation()).isSameAs(parent);
		}
		finally {
			parent.stop();
		}
	}

	@Test
	void zeroDelayStillProducesObservation() throws Exception {
		assertThat(new SlowDemoController(this.registry, Duration.ZERO).slow()).containsEntry("delay_ms", 0L);
		assertThat(this.completed).hasSize(1);
	}

	@Test
	void rejectsUnboundedDelay() {
		for (Duration delay : List.of(Duration.ofMillis(-1), Duration.ofMillis(5001))) {
			assertThatIllegalArgumentException().isThrownBy(() -> new SlowDemoController(this.registry, delay))
				.withMessageContaining("between 0 and 5 seconds");
		}
	}

	@Test
	void interruptionIsRecordedAndPreserved() {
		try {
			Thread.currentThread().interrupt();
			assertThatThrownBy(() -> new SlowDemoController(this.registry, Duration.ofSeconds(2)).slow())
				.isInstanceOf(InterruptedException.class);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
		}
		assertThat(this.completed).hasSize(1);
		assertThat(this.completed.get(0).getError()).isInstanceOf(InterruptedException.class);
		assertThat(this.registry.getCurrentObservation()).isNull();
	}

}
