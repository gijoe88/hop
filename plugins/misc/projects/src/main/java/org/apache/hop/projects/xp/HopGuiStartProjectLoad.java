/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.projects.xp;

import org.apache.commons.lang.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.history.AuditEvent;
import org.apache.hop.history.AuditManager;
import org.apache.hop.projects.config.ProjectsConfig;
import org.apache.hop.projects.config.ProjectsConfigSingleton;
import org.apache.hop.projects.environment.LifecycleEnvironment;
import org.apache.hop.projects.gui.ProjectsGuiPlugin;
import org.apache.hop.projects.project.Project;
import org.apache.hop.projects.project.ProjectConfig;
import org.apache.hop.projects.util.ProjectsUtil;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;

import java.util.List;

@ExtensionPoint(
    id = "HopGuiStartProjectLoad",
    description = "Load the previously used project",
    extensionPointId = "HopGuiStart")
/** set the debug level right before the transform starts to run */
public class HopGuiStartProjectLoad implements IExtensionPoint {

  @Override
  public void callExtensionPoint(ILogChannel logChannelInterface, IVariables variables, Object o)
      throws HopException {

    HopGui hopGui = HopGui.getInstance();

    try {
      ProjectsConfig config = ProjectsConfigSingleton.getConfig();

      // Only move forward if the "projects" system is enabled.
      //
      if (ProjectsConfigSingleton.getConfig().isEnabled()) {
        logChannelInterface.logBasic("Projects enabled");

        // What is the last used project?
        //
        String lastProjectName = null;

        // Let's see in the audit logs what the last opened project is.
        //
        List<AuditEvent> auditEvents = AuditManager.findEvents(
                ProjectsUtil.STRING_PROJECTS_AUDIT_GROUP,
                ProjectsUtil.STRING_PROJECT_AUDIT_TYPE,
                "open",
                1,
                true);
        if (auditEvents.isEmpty()) {
          lastProjectName = config.getDefaultProject();
        } else {
          logChannelInterface.logDetailed(
              "Audit events found for hop-gui/project : " + auditEvents.size());

          AuditEvent lastEvent = auditEvents.get(0);
          long eventTime = lastEvent.getDate().getTime();

          lastProjectName = lastEvent.getName();
          if (config.findProjectConfig(lastProjectName) == null) {
            // The last existing project to open was not found.
            //
            lastProjectName=null;
          }
        }

        if (StringUtils.isNotEmpty(lastProjectName)) {
          ProjectConfig projectConfig = config.findProjectConfig(lastProjectName);
          if (projectConfig != null) {
            Project project = projectConfig.loadProject(variables);

            logChannelInterface.logBasic("Enabling project : '" + lastProjectName + "'");

            LifecycleEnvironment lastEnvironment = null;

            // What was the last environment for this project?
            //
            List<AuditEvent> envEvents = AuditManager.findEvents(
                    ProjectsUtil.STRING_PROJECTS_AUDIT_GROUP,
                    ProjectsUtil.STRING_ENVIRONMENT_AUDIT_TYPE,
                    "open",
                    100,
                    true);

            // Find the last selected environment for this project.
            //
            for (AuditEvent envEvent : envEvents) {
              LifecycleEnvironment environment = config.findEnvironment(envEvent.getName());
              if (environment!=null && lastProjectName.equals(environment.getProjectName())) {
                lastEnvironment = environment;
                break;
              }
            }

            // Set system variables for HOP_HOME, HOP_METADATA_FOLDER, ...
            // Sets the namespace in HopGui to the name of the project.
            //
            ProjectsGuiPlugin.enableHopGuiProject(lastProjectName, project, lastEnvironment);

            // Don't open the files again in the HopGui startup code.
            //
            hopGui.setOpeningLastFiles(false);
          }
        }
      } else {
        logChannelInterface.logBasic("No last projects history found");
      }
    } catch (Exception e) {
      new ErrorDialog(hopGui.getShell(), "Error", "Error initializing the Projects system", e);
    }
  }
}
