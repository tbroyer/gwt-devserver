package net.ltgt.gwt.devserver.client;

/**
 * Tests that our dependency exclusions still allow using GWTTestCase.
 */
public class GWTTestCase extends com.google.gwt.junit.client.GWTTestCase {
  @Override
  public String getModuleName() {
    return "net.ltgt.gwt.devserver.GWTTestCase";
  }

  public void testGWTTestCase() {
    // no-op
  }
}
