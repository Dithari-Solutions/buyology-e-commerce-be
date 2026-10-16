# Backend test execution

Use JDK 17 (matching CI) or a compatible newer JDK. Maven configures Mockito's
startup agent automatically; no local absolute `-javaagent` path is required.
The resolved test dependency supplies the agent, following the
[Mockito Maven setup](https://javadoc.io/static/org.mockito/mockito-core/5.14.2/org/mockito/Mockito.html#0.3).

Run unit tests:

```bash
./mvnw -B test -Dtest='*Test'
```

Run the same unit and PostgreSQL integration suite as CI, with Docker running:

```bash
./mvnw -B test -Dtest='*Test,*IT' -DfailIfNoTests=false
```

All tests use Mockito's default inline mock maker. Do not introduce individual
`mock-maker-subclass` overrides as an agent-attachment workaround. Mixing these
makers in Mockito 5.14.2 can fail its internal handler-ownership assertion when
recognizing interface mocks; registry iteration order makes the symptom vary
between test runs/JVMs. An interrupted `doThrow().when(...)` can then leave
unfinished-stubbing state for the following test.

The failed CI run [38911755447](https://github.com/Dithari-Solutions/buyology-e-commerce-be/actions/runs/38911755447)
reported 292 failures and 2 errors, starting with `MockUtil.getMockHandlerOrNull`
assertions after the partnership tests registered the subclass maker. The cart
reminder's `doThrow(...).when(...).sendToUser(...)` was already complete; its
unfinished-stubbing error was a consequence of the earlier assertion. The fix
removes the three maker overrides in the partnership tests and configures startup
instrumentation in Surefire. Test assertions and all CI test selections remain
enabled; production application behavior is unchanged by this fix.

Local verification: all 838 selected unit tests passed on Java 17 and Java 21.
The 56 integration tests require Docker and are validated by the GitHub test job.
