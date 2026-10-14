package app.sevacenter.domain;

import java.util.List;

/** The few DNS answers domain verification needs. Empty list = no such record (or lookup failed). */
public interface DnsLookup {

    List<String> txt(String name);

    List<String> cname(String name);

    List<String> a(String name);
}
