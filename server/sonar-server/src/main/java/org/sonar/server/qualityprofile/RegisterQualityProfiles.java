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
package org.sonar.server.qualityprofile;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.ServerComponent;
import org.sonar.api.profiles.ProfileDefinition;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRuleParam;
import org.sonar.api.utils.TimeProfiler;
import org.sonar.api.utils.ValidationMessages;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.qualityprofile.db.QualityProfileDto;
import org.sonar.core.template.LoadedTemplateDto;
import org.sonar.server.db.DbClient;
import org.sonar.server.platform.PersistentSettings;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Synchronize Quality profiles during server startup
 */
public class RegisterQualityProfiles implements ServerComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterQualityProfiles.class);
  private static final String DEFAULT_PROFILE_NAME = "Sonar way";

  private final PersistentSettings settings;
  private final List<ProfileDefinition> definitions;
  private final BuiltInProfiles builtInProfiles;
  private final DbClient dbClient;
  private final QProfileFactory profileFactory;
  private final RuleActivator ruleActivator;

  /**
   * To be kept when no ProfileDefinition are injected
   */
  public RegisterQualityProfiles(PersistentSettings settings, BuiltInProfiles builtInProfiles,
    DbClient dbClient, QProfileFactory profileFactory, RuleActivator ruleActivator) {
    this(settings, builtInProfiles, dbClient, profileFactory, ruleActivator, Collections.<ProfileDefinition>emptyList());
  }

  public RegisterQualityProfiles(PersistentSettings settings, BuiltInProfiles builtInProfiles,
    DbClient dbClient, QProfileFactory profileFactory, RuleActivator ruleActivator,
    List<ProfileDefinition> definitions) {
    this.settings = settings;
    this.builtInProfiles = builtInProfiles;
    this.dbClient = dbClient;
    this.profileFactory = profileFactory;
    this.ruleActivator = ruleActivator;
    this.definitions = definitions;
  }

  public void start() {
    TimeProfiler profiler = new TimeProfiler(LOGGER).start("Register Quality Profiles");

    DbSession session = dbClient.openSession(false);
    try {
      ListMultimap<String, RulesProfile> profilesByLanguage = profilesByLanguage();
      for (String language : profilesByLanguage.keySet()) {
        List<RulesProfile> defs = profilesByLanguage.get(language);
        verifyLanguage(language, defs);

        for (Map.Entry<String, Collection<RulesProfile>> entry : profilesByName(defs).entrySet()) {
          String name = entry.getKey();
          QProfileName profileName = new QProfileName(language, name);
          if (shouldRegister(profileName, session)) {
            register(profileName, entry.getValue(), session);
            session.commit();
          }
          builtInProfiles.put(language, name);
        }
        setDefault(language, defs, session);
        session.commit();
      }

    } finally {
      session.close();
      profiler.stop();
    }
  }

  private static void verifyLanguage(String language, List<RulesProfile> profiles) {
    if (profiles.isEmpty()) {
      LOGGER.warn("No Quality Profile defined for language: " + language);
    }

    Set<String> defaultProfileNames = defaultProfileNames(profiles);
    if (defaultProfileNames.size() > 1) {
      throw new IllegalStateException("Several Quality profiles are flagged as default for the language " + language + ": " + defaultProfileNames);
    }
  }

  private void register(QProfileName name, Collection<RulesProfile> profiles, DbSession session) {
    LOGGER.info("Register profile " + name);

    QualityProfileDto profileDto = dbClient.qualityProfileDao().getByNameAndLanguage(name.getName(), name.getLanguage(), session);
    if (profileDto != null) {
      profileFactory.delete(session, profileDto.getKey(), true);
    }
    profileFactory.create(session, name);

    for (RulesProfile profile : profiles) {
      for (org.sonar.api.rules.ActiveRule activeRule : profile.getActiveRules()) {
        RuleKey ruleKey = RuleKey.of(activeRule.getRepositoryKey(), activeRule.getRuleKey());
        RuleActivation activation = new RuleActivation(ruleKey);
        activation.setSeverity(activeRule.getSeverity() != null ? activeRule.getSeverity().name() : null);
        for (ActiveRuleParam param : activeRule.getActiveRuleParams()) {
          activation.setParameter(param.getKey(), param.getValue());
        }
        ruleActivator.activate(session, activation, name);
      }
    }

    LoadedTemplateDto template = new LoadedTemplateDto(templateKey(name), LoadedTemplateDto.QUALITY_PROFILE_TYPE);
    dbClient.loadedTemplateDao().insert(template, session);
  }

  private void setDefault(String language, List<RulesProfile> profileDefs, DbSession session) {
    String propertyKey = "sonar.profile." + language;
    boolean upToDate = false;
    String currentDefault = settings.getString(propertyKey);
    if (currentDefault != null) {
      // check validity
      QualityProfileDto profile = dbClient.qualityProfileDao().getByNameAndLanguage(currentDefault, language, session);
      if (profile != null) {
        upToDate = true;
      }
    }

    if (!upToDate) {
      String defaultProfileName = defaultProfileName(profileDefs);
      LOGGER.info("Set default " + language + " profile: " + defaultProfileName);
      settings.saveProperty(propertyKey, defaultProfileName);
    }
  }

  /**
   * @return profiles by language
   */
  private ListMultimap<String, RulesProfile> profilesByLanguage() {
    ListMultimap<String, RulesProfile> byLang = ArrayListMultimap.create();
    for (ProfileDefinition definition : definitions) {
      ValidationMessages validation = ValidationMessages.create();
      RulesProfile profile = definition.createProfile(validation);
      validation.log(LOGGER);
      if (profile != null && !validation.hasErrors()) {
        byLang.put(StringUtils.lowerCase(profile.getLanguage()), profile);
      }
    }
    return byLang;
  }

  private static Map<String, Collection<RulesProfile>> profilesByName(List<RulesProfile> profiles) {
    return Multimaps.index(profiles, new Function<RulesProfile, String>() {
      public String apply(@Nullable RulesProfile profile) {
        return profile != null ? profile.getName() : null;
      }
    }).asMap();
  }

  private static String defaultProfileName(List<RulesProfile> profiles) {
    String defaultName = null;
    boolean hasSonarWay = false;

    for (RulesProfile profile : profiles) {
      if (profile.getDefaultProfile()) {
        defaultName = profile.getName();
      } else if (DEFAULT_PROFILE_NAME.equals(profile.getName())) {
        hasSonarWay = true;
      }
    }

    if (StringUtils.isBlank(defaultName) && !hasSonarWay && !profiles.isEmpty()) {
      defaultName = profiles.get(0).getName();
    }

    return StringUtils.defaultIfBlank(defaultName, DEFAULT_PROFILE_NAME);
  }

  private static Set<String> defaultProfileNames(Collection<RulesProfile> profiles) {
    Set<String> names = Sets.newTreeSet();
    for (RulesProfile profile : profiles) {
      if (profile.getDefaultProfile()) {
        names.add(profile.getName());
      }
    }
    return names;
  }

  private boolean shouldRegister(QProfileName key, DbSession session) {
    return dbClient.loadedTemplateDao()
      .countByTypeAndKey(LoadedTemplateDto.QUALITY_PROFILE_TYPE, templateKey(key), session) == 0;
  }

  static String templateKey(QProfileName key) {
    return StringUtils.lowerCase(key.getLanguage()) + ":" + key.getName();
  }
}
