/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.core;

import org.junit.Test;

import java.util.Date;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class UtcDateUtilsTest {

  @Test
  public void parse_then_format() throws Exception {
    Date date = UtcDateUtils.parseDateTime("2014-01-14T14:00:00+0200");
    assertThat(UtcDateUtils.formatDateTime(date)).isEqualTo("2014-01-14T12:00:00+0000");
  }

  @Test
  public void fail_if_bad_format() throws Exception {
    try {
      UtcDateUtils.parseDateTime("2014-01-14");
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Fail to parse date: 2014-01-14");
    }
  }
}
