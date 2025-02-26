const puppeteer = require('puppeteer');
const assert = require('assert');
const cas = require('../../cas.js');

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);

    let service1 = "https://apereo.github.io";
    await cas.goto(page, `https://localhost:8443/cas/login?service=${service1}`);
    await cas.loginWith(page);
    let ticket = await cas.assertTicketParameter(page);
    let body = await cas.doRequest(`https://localhost:8443/cas/p3/serviceValidate?service=${service1}&ticket=${ticket}`);
    await cas.log(body);
    assert(body.includes('<cas:serviceResponse xmlns:cas=\'http://www.yale.edu/tp/cas\'>'));
    assert(body.includes('<cas:user>CASUSER@EXAMPLE.ORG</cas:user>'));
    assert(body.includes('<cas:credentialType>UsernamePasswordCredential</cas:credentialType>'));
    assert(body.includes('<cas:isFromNewLogin>true</cas:isFromNewLogin>'));
    assert(body.includes('<cas:authenticationMethod>STATIC</cas:authenticationMethod>'));
    assert(body.includes('<cas:successfulAuthenticationHandlers>STATIC</cas:successfulAuthenticationHandlers>'));
    assert(body.includes('<cas:longTermAuthenticationRequestTokenUsed>false</cas:longTermAuthenticationRequestTokenUsed>'));
    assert(body.includes('<cas:memberOf>Colleague Admins</cas:memberOf>'));

    let service2 = "http://localhost:9889/anything/cas";
    await cas.goto(page, `https://localhost:8443/cas/login?service=${service2}`);
    ticket = await cas.assertTicketParameter(page);
    body = await cas.doRequest(`https://localhost:8443/cas/p3/serviceValidate?service=${service2}&ticket=${ticket}&format=JSON`);
    await cas.log(body);
    let json = JSON.parse(body);
    let authenticationSuccess = json.serviceResponse.authenticationSuccess;
    assert(authenticationSuccess.user === "CAS_USER_APEREO@APEREO.ORG");
    await browser.close();
})();
