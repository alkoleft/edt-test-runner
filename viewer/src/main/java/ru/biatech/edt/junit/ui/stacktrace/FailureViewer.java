/*******************************************************************************
 * Copyright (c) 2022 BIA-Technologies Limited Liability Company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package ru.biatech.edt.junit.ui.stacktrace;

import com._1c.g5.v8.dt.stacktraces.model.IStacktrace;
import com._1c.g5.v8.dt.stacktraces.model.IStacktraceElement;
import com._1c.g5.v8.dt.stacktraces.model.IStacktraceError;
import com._1c.g5.v8.dt.stacktraces.model.IStacktraceFrame;
import com._1c.g5.v8.dt.stacktraces.model.IStacktraceParser;
import com.google.common.base.Strings;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.util.IOpenEventListener;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IWorkbenchPage;
import ru.biatech.edt.junit.TestViewerPlugin;
import ru.biatech.edt.junit.model.Factory;
import ru.biatech.edt.junit.model.TestElement;
import ru.biatech.edt.junit.model.TestStatus;
import ru.biatech.edt.junit.ui.ImageProvider;
import ru.biatech.edt.junit.ui.JUnitMessages;
import ru.biatech.edt.junit.ui.report.TestRunnerViewPart;
import ru.biatech.edt.junit.ui.stacktrace.actions.CompareResultsAction;
import ru.biatech.edt.junit.ui.stacktrace.actions.CopyTraceAction;
import ru.biatech.edt.junit.v8utils.BslSourceDisplay;
import ru.biatech.edt.junit.v8utils.Services;

/**
 * Класс для отображения и взаимодействия с ошибками тестирования
 */
public class FailureViewer {

  private StackTracesTreeView viewer;
  private final TestRunnerViewPart viewPart;
  private TestElement testElement;
  private final IStacktraceParser stacktraceParser;
  private final Clipboard clipboard;
  private final ViewForm parent;

  // Actions
  private CopyTraceAction copyTraceAction;
  private CompareResultsAction compareAction;
  private OpenAction openAction;

  public FailureViewer(TestRunnerViewPart viewPart, ViewForm parent) {
    this.viewPart = viewPart;
    this.parent = parent;

    clipboard = new Clipboard(parent.getDisplay());
    stacktraceParser = Services.getStacktraceParser();

    createControls();
  }

  /**
   * Отображает информацию об ошибках теста
   * @param testElement тест содержащий ошибки
   */
  public void viewFailure(TestElement testElement) {
    this.testElement = testElement;
    boolean testElementExist = testElement != null;

    String trace = testElementExist ? testElement.getTrace() : null;
    if (!Strings.isNullOrEmpty(trace)) {
      viewStackTrace(stacktraceParser.parse(trace, testElement.getTestName(), null));
    } else {
      clear();
    }

    compareAction.handleTestSelected(testElement);
    copyTraceAction.handleTestSelected(testElement);
    openAction.setEnabled(testElementExist);
  }

  public void clear() {
    viewer.setStackTrace(null);
  }

  public void dispose() {
    clipboard.dispose();
    viewer.getTree().dispose();
  }

  private void viewStackTrace(IStacktrace stacktrace) {
    this.viewer.setStackTrace(stacktrace);
  }

  private void createControls() {
    GridLayoutFactory.fillDefaults().applyTo(parent);
    GridDataFactory.fillDefaults().grab(true, true).applyTo(parent);

    createViewer();
    createLabel();
    registerActions();

    parent.setContent(viewer.getTree());
  }

  private void registerActions() {
    compareAction = new CompareResultsAction(parent);
    copyTraceAction = new CopyTraceAction(parent, clipboard);
    openAction = new OpenAction();

    // Double click
    IOpenEventListener openListener = e -> openSelected();
    OpenStrategy handler = new OpenStrategy(viewer.getTree());
    handler.addOpenListener(openListener);

    // ToolBar
    ToolBar toolBar = new ToolBar(parent, SWT.FLAT | SWT.WRAP);
    parent.setTopCenter(toolBar);
    ToolBarManager failureToolBarManager = new ToolBarManager(toolBar);
    compareAction.setEnabled(false);
    failureToolBarManager.add(compareAction);
    failureToolBarManager.update(true);

    // Menu
    MenuManager menuManager = new MenuManager();
    menuManager.add(openAction);
    menuManager.add(compareAction);
    menuManager.add(copyTraceAction);
    Menu menu = menuManager.createContextMenu(viewer.getTree());
    viewer.getTree().setMenu(menu);
    viewPart.getSite().registerContextMenu(menuManager, viewer);

    parent.addDisposeListener((e) -> {
      menuManager.dispose();
      failureToolBarManager.dispose();
      handler.removeOpenListener(openListener);
    });


  }

  private void createViewer() {
    this.viewer = new StackTracesTreeView(parent);
  }

  private void createLabel() {
    CLabel label = new CLabel(parent, SWT.NONE);
    label.setText(JUnitMessages.TestRunnerViewPart_label_failure);
    label.setImage(viewPart.getImageProvider().getStackViewIcon());
    parent.setTopLeft(label);
  }

  private void openSelected() {
    if (viewer.getSelection().isEmpty()) {
      return;
    }
    var element = viewer.getSelected();

    if (element instanceof IStacktraceError && testElement.isComparisonFailure()) {
      compareAction.run();
    } else if (element instanceof IStacktraceFrame) {
      openAction.run();
    }
  }

  private class OpenAction extends Action {

    private OpenAction() {
      setImageDescriptor(TestViewerPlugin.ui().getImageDescriptor(ImageProvider.GOTO_ICON));
      setText("Goto line");
      setEnabled(false);
    }

    @Override
    public void run() {
      IStacktraceElement element;
      if (viewer.getSelection().isEmpty() || !((element = viewer.getSelected()) instanceof IStacktraceFrame)) {
        return;
      }
      IWorkbenchPage page = viewPart.getSite().getPage();
      BslSourceDisplay.INSTANCE.displayBslSource(element, page, false);
    }
  }
}
