package com.adguard.webbatterytester;

import com.google.common.net.InternetDomainName;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {

    @Test
    public void testInternetDomainName() {
        System.out.print(InternetDomainName.from("google.com").parts().get(0));
    }
}