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
package org.sonar.server.db.migrations.v451;

import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.core.persistence.TestDatabase;
import org.sonar.server.db.migrations.DatabaseMigration;

import static org.fest.assertions.Assertions.assertThat;

public class DeleteUnescapedActivitiesTest {

  @ClassRule
  public static TestDatabase db = new TestDatabase().schema(DeleteUnescapedActivitiesTest.class, "schema.sql");

  DatabaseMigration migration;

  @Test
  public void execute() throws Exception {
    migration = new DeleteUnescapedActivities(db.database());
    db.prepareDbUnit(getClass(), "execute.xml");
    migration.execute();
    db.assertDbUnit(getClass(), "execute-result.xml", "activities");
  }

  @Test
  public void is_unescaped() throws Exception {
    assertThat(DeleteUnescapedActivities.isUnescaped(
      "ruleKey=findbugs:PT_RELATIVE_PATH_TRAVERSAL;profileKey=java-findbugs-74105;severity=MAJOR;" +
        "key=java-findbugs-74105:findbugs:PT_RELATIVE_PATH_TRAVERSAL"))
      .isFalse();
    assertThat(DeleteUnescapedActivities.isUnescaped(null)).isFalse();
    assertThat(DeleteUnescapedActivities.isUnescaped("")).isFalse();
    assertThat(DeleteUnescapedActivities.isUnescaped("foo=bar")).isFalse();
    assertThat(DeleteUnescapedActivities.isUnescaped("param_xpath=/foo/bar")).isFalse();

    assertThat(DeleteUnescapedActivities.isUnescaped("param_xpath=/foo/bar;foo;ruleKey=S001")).isTrue();
    assertThat(DeleteUnescapedActivities.isUnescaped("param_xpath=/foo=foo;ruleKey=S001")).isTrue();

  }
}
