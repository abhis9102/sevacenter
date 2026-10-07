// Test cases for endpoint-missing-role-check.yaml. Run: semgrep --test .semgrep/
// "ruleid:" = the rule must flag the next line; "ok:" = it must not. Not compiled, not scanned
// (.semgrepignore); it only has to parse.

@RestController
@RequestMapping("/api/v1/widgets")
class WidgetController {

    // ruleid: endpoint-missing-role-check
    @GetMapping
    public List<Widget> list() { return service.list(); }

    // ruleid: endpoint-missing-role-check
    @PostMapping("/{id}/archive")
    public Widget archive(@PathVariable long id) { return service.archive(id); }

    // Other annotations around the mapping don't hide it.
    // ruleid: endpoint-missing-role-check
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable long id) { service.delete(id); }

    // ruleid: endpoint-missing-role-check
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public Widget replace(@PathVariable long id) { return service.replace(id); }

    // "public" only counts as the /api/v1/public/ prefix, not a look-alike.
    // ruleid: endpoint-missing-role-check
    @GetMapping("/api/v1/publicity")
    public Widget lookAlike() { return service.first(); }

    // ruleid: endpoint-missing-role-check
    @GetMapping("/api/v1/widgets/public/all")
    public List<Widget> publicInTheMiddle() { return service.list(); }

    // ok: endpoint-missing-role-check
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public Widget update(@PathVariable long id) { return service.update(id); }

    // ok: endpoint-missing-role-check
    @PreAuthorize("hasRole('LEADER')")
    @PatchMapping("/{id}")
    public Widget patch(@PathVariable long id) { return service.patch(id); }

    // ok: endpoint-missing-role-check
    @GetMapping("/api/v1/public/widgets")
    public List<Widget> publicList() { return service.publicList(); }

    // ok: endpoint-missing-role-check
    @PostMapping("/api/v1/portal/widgets")
    public Widget devoteeAction() { return service.forDevotee(); }

    // Not an endpoint.
    // ok: endpoint-missing-role-check
    private Widget helper() { return null; }
}

// A class-level role check covers every method.
@RestController
@PreAuthorize("hasRole('LEADER')")
class LeaderOnlyController {

    // ok: endpoint-missing-role-check
    @GetMapping("/api/v1/leader-things")
    public List<Thing> list() { return service.list(); }
}

// Allowlisted: self-service, identity comes from the session.
@RestController
class MeController {

    // ok: endpoint-missing-role-check
    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal StaffUser user) { return Me.of(user); }
}

// Not a REST controller: out of scope.
@RestControllerAdvice
class SomeAdvice {

    // ok: endpoint-missing-role-check
    @GetMapping("/advice")
    public String notReallyAnEndpoint() { return ""; }
}
