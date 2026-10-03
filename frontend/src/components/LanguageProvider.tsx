"use client";

import { createContext, useContext, useEffect, useState } from "react";
import {
  DICTIONARIES,
  getInitialLanguage,
  persistLanguage,
  type Language,
  type Translations,
} from "@/lib/i18n";

interface LanguageContextValue {
  lang: Language;
  setLang: (lang: Language) => void;
  t: Translations;
}

const LanguageContext = createContext<LanguageContextValue>({
  lang: "en",
  setLang: () => {},
  t: DICTIONARIES.en,
});

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [lang, setLangState] = useState<Language>("en");

  useEffect(() => {
    const initial = getInitialLanguage();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- read once from the browser (URL fragment / cookie)
    setLangState(initial);
    document.documentElement.lang = initial;
  }, []);

  function setLang(newLang: Language) {
    setLangState(newLang);
    persistLanguage(newLang);
  }

  return (
    <LanguageContext.Provider value={{ lang, setLang, t: DICTIONARIES[lang] }}>
      {children}
    </LanguageContext.Provider>
  );
}

export function useLanguage() {
  return useContext(LanguageContext);
}
