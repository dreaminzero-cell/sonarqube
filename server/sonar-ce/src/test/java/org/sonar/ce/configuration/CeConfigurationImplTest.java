/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.ce.configuration;

import java.util.Random;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.MessageException;

import static java.lang.Math.abs;
import static org.assertj.core.api.Assertions.assertThat;

public class CeConfigurationImplTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private SimpleWorkerCountProvider workerCountProvider = new SimpleWorkerCountProvider();

  @Test
  public void getWorkerCount_returns_1_when_there_is_no_WorkerCountProvider() {
    assertThat(new CeConfigurationImpl().getWorkerCount()).isEqualTo(1);
  }

  @Test
  public void getWorkerThreadCount_returns_1_when_there_is_no_WorkerCountProvider() {
    assertThat(new CeConfigurationImpl().getWorkerMaxCount()).isEqualTo(1);
  }

  @Test
  public void getWorkerCount_returns_value_returned_by_WorkerCountProvider_when_available() {
    int value = 1 + Math.abs(new Random().nextInt());
    workerCountProvider.set(value);

    assertThat(new CeConfigurationImpl(workerCountProvider).getWorkerCount()).isEqualTo(value);
  }

  @Test
  public void getWorkerThreadCount_returns_10_whichever_the_value_returned_by_WorkerCountProvider() {
    int value = 1 + Math.abs(new Random().nextInt(10));
    workerCountProvider.set(value);
    
    assertThat(new CeConfigurationImpl(workerCountProvider).getWorkerMaxCount()).isEqualTo(10);
  }

  @Test
  public void constructor_throws_MessageException_when_WorkerCountProvider_returns_0() {
    workerCountProvider.set(0);

    expectMessageException(0);

    new CeConfigurationImpl(workerCountProvider);
  }

  @Test
  public void constructor_throws_MessageException_when_WorkerCountProvider_returns_less_than_0() {
    int value = -1 - abs(new Random().nextInt());
    workerCountProvider.set(value);

    expectMessageException(value);

    new CeConfigurationImpl(workerCountProvider);
  }

  private void expectMessageException(int value) {
    expectedException.expect(MessageException.class);
    expectedException.expectMessage("Worker count '" + value + "' is invalid. " +
        "It must an integer strictly greater than 0");
  }

  @Test
  public void getCleanCeTasksInitialDelay_returns_1() {
    assertThat(new CeConfigurationImpl().getCleanCeTasksInitialDelay())
        .isEqualTo(1L);
    workerCountProvider.set(1);
    assertThat(new CeConfigurationImpl(workerCountProvider).getCleanCeTasksInitialDelay())
        .isEqualTo(1L);
  }

  @Test
  public void getCleanCeTasksDelay_returns_10() {
    assertThat(new CeConfigurationImpl().getCleanCeTasksDelay())
        .isEqualTo(10L);
    workerCountProvider.set(1);
    assertThat(new CeConfigurationImpl(workerCountProvider).getCleanCeTasksDelay())
        .isEqualTo(10L);
  }

  private static final class SimpleWorkerCountProvider implements WorkerCountProvider {
    private int value = 0;

    void set(int value) {
      this.value = value;
    }

    @Override
    public int get() {
      return value;
    }
  }
}