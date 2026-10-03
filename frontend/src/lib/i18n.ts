export type Language = "en" | "hi";

export interface Translations {
  common: {
    loading: string;
    save: string;
    saving: string;
    cancel: string;
    delete: string;
    deleting: string;
    edit: string;
    back: string;
    signOut: string;
    required: string;
    optional: string;
    search: string;
    close: string;
    confirm: string;
    error: string;
    success: string;
    permanentAction: string;
  };
  nav: {
    devotees: string;
    staff: string;
    donations: string;
    home: string;
    profile: string;
  };
  roles: {
    TRUST_ADMIN: string;
    LEADER: string;
    MEMBER: string;
  };
  consent: {
    IN_PERSON: string;
    PHONE: string;
    ONLINE_FORM: string;
    WRITTEN: string;
  };
  profile: {
    title: string;
    description: string;
    tabs: {
      account: string;
      security: string;
      notifications: string;
    };
    account: {
      heading: string;
      role: string;
      tenant: string;
      email: string;
      memberSince: string;
      status: string;
      displayName: string;
      displayNamePlaceholder: string;
      saveDisplayName: string;
      savedSuccess: string;
    };
    security: {
      heading: string;
      description: string;
      currentPassword: string;
      newPassword: string;
      confirmPassword: string;
      newPasswordHint: string;
      savePassword: string;
      savingPassword: string;
      passwordMismatch: string;
      passwordTooShort: string;
      passwordSame: string;
      savedSuccess: string;
    };
    notifications: {
      heading: string;
      description: string;
      devoteesTitle: string;
      devoteesDesc: string;
      donationsTitle: string;
      donationsDesc: string;
      securityTitle: string;
      securityDesc: string;
      saveNotifications: string;
      savedSuccess: string;
    };
    avatar: {
      heading: string;
      changePhoto: string;
      uploadPhoto: string;
      uploading: string;
      removePhoto: string;
      removing: string;
      photoRemoved: string;
      photoUploaded: string;
      hint: string;
      tooLarge: string;
      invalidFormat: string;
    };
  };
  devotees: {
    title: string;
    description: string;
    addDevotee: string;
    exportCsv: string;
    exporting: string;
    importCsv: string;
    searchPlaceholderMember: string;
    searchPlaceholderLeader: string;
    searchButton: string;
    clearSearch: string;
    colName: string;
    colPhone: string;
    colEmail: string;
    colCity: string;
    colState: string;
    colPincode: string;
    colDob: string;
    colConsent: string;
    colAdded: string;
    contactHiddenNote: string;
    maskedLabel: string;
    maskedNoteTitle: string;
    maskedNoteBody: string;
    emptyState: string;
    pageShowing: (start: number, end: number, total: number) => string;
    prev: string;
    next: string;
    form: {
      newTitle: string;
      newDescription: string;
      editTitle: string;
      fullNameLabel: string;
      fullNamePlaceholder: string;
      phoneLabel: string;
      phonePlaceholder: string;
      emailLabel: string;
      emailPlaceholder: string;
      addressLabel: string;
      addressPlaceholder: string;
      cityLabel: string;
      cityPlaceholder: string;
      stateLabel: string;
      statePlaceholder: string;
      pincodeLabel: string;
      pincodePlaceholder: string;
      dobLabel: string;
      consentLabel: string;
      consentHelp: string;
      saveButton: string;
      savingButton: string;
    };
    detail: {
      contactDetails: string;
      addressHeading: string;
      consentHeading: string;
      consentRecordedAt: string;
      consentRecordedBy: string;
      auditHeading: string;
      createdAt: string;
      updatedAt: string;
      eraseButton: string;
      eraseConfirmTitle: string;
      eraseConfirmMessage: string;
      eraseConfirmAction: string;
    };
    importPage: {
      title: string;
      description: string;
      backLink: string;
      formatTitle: string;
      formatDescription: string;
      downloadTemplate: string;
      fileLabel: string;
      importButton: string;
      importing: string;
      importCompleteSingle: string;
      importCompleteMultiple: (count: number) => string;
      viewDevotees: string;
      rowsToFix: string;
      colLine: string;
      colColumn: string;
      colProblem: string;
      chooseFileError: string;
    };
  };
  donations: {
    title: string;
    description: string;
    recordDonation: string;
    totalCollection: string;
    totalEntries: string;
    filterFrom: string;
    filterTo: string;
    allModes: string;
    colDate: string;
    colDonor: string;
    colAmount: string;
    colMode: string;
    colPurpose: string;
    colSource: string;
    colActions: string;
    sourceOnline: string;
    sourceCounter: string;
    receiptButton: string;
    reverseButton: string;
    emptyState: string;
    recordModalTitle: string;
    donorNameLabel: string;
    amountLabel: string;
    modeLabel: string;
    purposeLabel: string;
    referenceLabel: string;
    dateLabel: string;
    submitRecord: string;
    recording: string;
    reversalModalTitle: string;
    reversalReasonLabel: string;
    submitReversal: string;
    reversing: string;
    pageShowing: (start: number, end: number, total: number) => string;
  };
  errors: Record<string, string>;
  auth: {
    signIn: string;
    forgotPassword: string;
    registerTrust: string;
    loginTitle: string;
    email: string;
    password: string;
    signInButton: string;
    noTenantTitle: string;
    noTenantDesc: string;
    noTenantLocalNote: string;
    noTenantRegisterButton: string;
    newStaffHelp: string;
    invalidCredentials: string;
    rateLimited: string;
    setupDone: string;
    resetDone: string;
    register: {
      title: string;
      subtitle: string;
      trustName: string;
      trustNamePlaceholder: string;
      subdomain: string;
      subdomainHelp: string;
      adminName: string;
      adminEmail: string;
      adminPassword: string;
      passwordHelp: string;
      submit: string;
      submitting: string;
      successTitle: string;
      successMessage: string;
      openPortal: string;
      alreadyHaveAccount: string;
      invalidSlug: string;
    };
    forgot: {
      title: string;
      subtitle: string;
      instructions: string;
      backToLogin: string;
    };
    reset: {
      title: string;
      subtitle: string;
      newPassword: string;
      confirmPassword: string;
      submit: string;
      submitting: string;
      mismatch: string;
      tokenMissing: string;
    };
  };
  portal: {
    title: string;
    subtitle: string;
    donateTab: string;
    historyTab: string;
    donateHeading: string;
    donateSubtitle: string;
    quickAmounts: string;
    customAmount: string;
    purpose: string;
    purposes: {
      general: string;
      annadanam: string;
      puja: string;
      construction: string;
      gaushala: string;
      deepam: string;
    };
    paymentMode: string;
    donorName: string;
    phoneOrEmail: string;
    city: string;
    donateButton: string;
    donating: string;
    noPanNotice: string;
    receiptSuccessTitle: string;
    receiptNumber: string;
    downloadReceipt: string;
    printReceipt: string;
    closeReceipt: string;
    loginToViewHistory: string;
    signInDevotee: string;
    phoneOtp: string;
    emailOtp: string;
    enterPhone: string;
    enterEmail: string;
    sendOtp: string;
    sendingOtp: string;
    enterOtp: string;
    verifyOtp: string;
    verifyingOtp: string;
    devOtpNotice: string;
    registerPrompt: string;
    registerButton: string;
    consentNotice: string;
    noDonationsYet: string;
    viewReceipt: string;
  };
}

export const DICTIONARIES: Record<Language, Translations> = {
  en: {
    common: {
      loading: "Loading…",
      save: "Save",
      saving: "Saving…",
      cancel: "Cancel",
      delete: "Delete",
      deleting: "Deleting…",
      edit: "Edit",
      back: "Back",
      signOut: "Sign out",
      required: "Required",
      optional: "Optional",
      search: "Search",
      close: "Close",
      confirm: "Confirm",
      error: "Error",
      success: "Success",
      permanentAction: "This action cannot be undone.",
    },
    nav: {
      devotees: "Devotees",
      staff: "Staff",
      donations: "Donations",
      home: "Home",
      profile: "Profile",
    },
    roles: {
      TRUST_ADMIN: "Trust admin",
      LEADER: "Leader",
      MEMBER: "Member",
    },
    consent: {
      IN_PERSON: "In person",
      PHONE: "By phone",
      ONLINE_FORM: "Online form",
      WRITTEN: "In writing",
    },
    profile: {
      title: "My Profile",
      description: "Manage your account details, password and notification preferences.",
      tabs: {
        account: "Account",
        security: "Security & Password",
        notifications: "Notifications",
      },
      account: {
        heading: "Account details",
        role: "Role",
        tenant: "Trust subdomain",
        email: "Email address",
        memberSince: "Member since",
        status: "Status",
        displayName: "Display name",
        displayNamePlaceholder: "e.g. Ramesh Sharma",
        saveDisplayName: "Save name",
        savedSuccess: "Display name updated successfully.",
      },
      security: {
        heading: "Change password",
        description: "Choose a strong password of at least 12 characters to protect your account and your trust's data.",
        currentPassword: "Current password",
        newPassword: "New password",
        confirmPassword: "Confirm new password",
        newPasswordHint: "At least 12 characters. Avoid simple patterns.",
        savePassword: "Change password",
        savingPassword: "Updating password…",
        passwordMismatch: "New passwords do not match.",
        passwordTooShort: "Password must be at least 12 characters.",
        passwordSame: "New password must be different from your current password.",
        savedSuccess: "Your password has been changed successfully.",
      },
      notifications: {
        heading: "Notification preferences",
        description: "Choose which updates and alerts you wish to receive.",
        devoteesTitle: "Devotee updates",
        devoteesDesc: "Notifications when new devotees are added or bulk imports complete.",
        donationsTitle: "Donations & 80G receipt alerts",
        donationsDesc: "Notifications when donations are recorded or 80G tax receipts generated.",
        securityTitle: "Security alerts",
        securityDesc: "Alerts on new logins, session renewals, or password changes.",
        saveNotifications: "Save preferences",
        savedSuccess: "Notification preferences updated successfully.",
      },
      avatar: {
        heading: "Profile photo",
        changePhoto: "Change photo",
        uploadPhoto: "Upload photo",
        uploading: "Uploading…",
        removePhoto: "Remove photo",
        removing: "Removing…",
        photoRemoved: "Profile photo removed.",
        photoUploaded: "Profile photo updated successfully.",
        hint: "PNG, JPEG, or WebP. Max 2 MB.",
        tooLarge: "Image must be 2 MB or less.",
        invalidFormat: "Only PNG, JPEG, and WebP images are allowed.",
      },
    },
    devotees: {
      title: "Devotees",
      description: "People connected to the trust, recorded with their consent.",
      addDevotee: "Add devotee",
      exportCsv: "Export CSV",
      exporting: "Exporting…",
      importCsv: "Import CSV",
      searchPlaceholderMember: "Search devotees by name…",
      searchPlaceholderLeader: "Search devotees by name, phone or email…",
      searchButton: "Search",
      clearSearch: "Clear",
      colName: "Name",
      colPhone: "Phone",
      colEmail: "Email",
      colCity: "City",
      colState: "State",
      colPincode: "Pincode",
      colDob: "Date of birth",
      colConsent: "Consent",
      colAdded: "Added",
      contactHiddenNote: "Contact details hidden for your role.",
      maskedLabel: "Hidden",
      maskedNoteTitle: "Contact details hidden for your role",
      maskedNoteBody: "As a member you see names, city and state in full; phone numbers show only the last 4 digits and emails only their first letter. Address, pincode and date of birth are hidden. Search works on names only.",
      emptyState: "No devotees found.",
      pageShowing: (start, end, total) => `Showing ${start}–${end} of ${total} devotees`,
      prev: "Previous",
      next: "Next",
      form: {
        newTitle: "New devotee",
        newDescription: "Record a new devotee. Consent is required before storing personal details.",
        editTitle: "Edit devotee",
        fullNameLabel: "Full name",
        fullNamePlaceholder: "e.g. Ramesh Sharma",
        phoneLabel: "Phone number",
        phonePlaceholder: "e.g. 98765 43210",
        emailLabel: "Email address",
        emailPlaceholder: "e.g. ramesh@example.org",
        addressLabel: "Street address",
        addressPlaceholder: "e.g. 12 Mandir Marg",
        cityLabel: "City",
        cityPlaceholder: "e.g. Varanasi",
        stateLabel: "State",
        statePlaceholder: "e.g. Uttar Pradesh",
        pincodeLabel: "Pincode",
        pincodePlaceholder: "e.g. 221001",
        dobLabel: "Date of birth",
        consentLabel: "Consent source",
        consentHelp: "How did this person consent to having their details stored?",
        saveButton: "Save devotee",
        savingButton: "Saving…",
      },
      detail: {
        contactDetails: "Contact details",
        addressHeading: "Address",
        consentHeading: "Consent record",
        consentRecordedAt: "Recorded at",
        consentRecordedBy: "Recorded by",
        auditHeading: "Audit log",
        createdAt: "Created",
        updatedAt: "Last updated",
        eraseButton: "Erase devotee",
        eraseConfirmTitle: "Erase devotee permanently?",
        eraseConfirmMessage: "This will erase this devotee's personal details. This action cannot be undone.",
        eraseConfirmAction: "Erase permanently",
      },
      importPage: {
        title: "Import devotees",
        description: "Add many devotees at once from a CSV file. Every row is checked with the same rules as the form; if any row has an error, nothing is imported.",
        backLink: "← All devotees",
        formatTitle: "File format",
        formatDescription: "CSV (UTF-8) with a header row and these columns. Only fullName and consentSource are required on each row.",
        downloadTemplate: "Download sample template",
        fileLabel: "CSV file",
        importButton: "Import",
        importing: "Importing…",
        importCompleteSingle: "1 devotee was added.",
        importCompleteMultiple: (count) => `${count} devotees were added.`,
        viewDevotees: "View devotees",
        rowsToFix: "Rows to fix",
        colLine: "Line",
        colColumn: "Column",
        colProblem: "Problem",
        chooseFileError: "Choose a CSV file first.",
      },
    },
    donations: {
      title: "Donation Ledger",
      description: "Track all incoming contributions, counter donations, and online devotee seva offerings in real time.",
      recordDonation: "Record Counter Donation",
      totalCollection: "Total Collection",
      totalEntries: "Total Contributions",
      filterFrom: "From Date",
      filterTo: "To Date",
      allModes: "All Payment Modes",
      colDate: "Date",
      colDonor: "Donor Name",
      colAmount: "Amount",
      colMode: "Mode",
      colPurpose: "Purpose / Seva",
      colSource: "Source",
      colActions: "Actions",
      sourceOnline: "Online Portal",
      sourceCounter: "Counter / Staff",
      receiptButton: "Receipt",
      reverseButton: "Reverse",
      emptyState: "No donations recorded for the selected period.",
      recordModalTitle: "Record Counter / Offline Donation",
      donorNameLabel: "Donor Name",
      amountLabel: "Amount (₹)",
      modeLabel: "Payment Mode",
      purposeLabel: "Purpose / Seva (optional)",
      referenceLabel: "Payment Reference / Cheque No. / Transaction ID (optional)",
      dateLabel: "Date Received",
      submitRecord: "Record Donation",
      recording: "Recording…",
      reversalModalTitle: "Reverse Donation",
      reversalReasonLabel: "Reason for reversal (minimum 10 characters)",
      submitReversal: "Confirm Reversal",
      reversing: "Reversing…",
      pageShowing: (start, end, total) => `Showing ${start} to ${end} of ${total} donations`,
    },
    errors: {
      invalid_credentials: "That email and password don't match an account here.",
      too_many_attempts: "Too many attempts. Wait a few minutes before trying again.",
      email_taken: "A staff member with this email already exists.",
      last_admin: "The trust must keep at least one active trust admin. Make someone else an admin first.",
      not_pending: "This person has already set up their account, so they don't need a new setup link.",
      invalid_or_expired_link: "This setup link is invalid, expired or already used. Ask your trust admin for a new one.",
      not_found: "That record doesn't exist (or was removed).",
      validation_failed: "Some fields need attention.",
      concurrent_modification: "Someone else changed this record at the same time. Reload it and try again.",
      upload_too_large: "That file is too large. The limit is 2 MB.",
      malformed_upload: "The upload was not readable. Choose the file again and retry.",
      too_many_rows: "The file has too many rows for one import. Split it into smaller files.",
      malformed_csv: "That file isn't valid CSV. Save it as CSV (UTF-8) and try again.",
      empty: "The file has no devotee rows.",
      invalid_rows: "Some rows have errors, so nothing was imported. Fix the rows below and upload again.",
      bad_header: "The header row doesn't match the expected columns. Start from an exported file or the template.",
      backend_unavailable: "The SevaCenter service isn't reachable right now. Try again in a moment.",
      csrf_unavailable: "Security token unavailable. Please refresh the page.",
    },
    auth: {
      signIn: "Sign in",
      forgotPassword: "Forgot password?",
      registerTrust: "Register a new Trust / Temple",
      loginTitle: "Sign in",
      email: "Email",
      password: "Password",
      signInButton: "Sign in",
      noTenantTitle: "Open your trust's own address",
      noTenantDesc: "Each temple or trust signs in at its own dedicated address, such as yourtrust.sevacenter.app.",
      noTenantLocalNote: "For local development use yourtrust.localhost:3000.",
      noTenantRegisterButton: "Register a new temple or trust",
      newStaffHelp: "New staff members get a one-time setup link from their trust admin.",
      invalidCredentials: "Email or password is incorrect.",
      rateLimited: "Too many sign-in attempts. Wait a few minutes, then try again.",
      setupDone: "Your password is set. Sign in to continue.",
      resetDone: "Your password has been reset. Sign in to continue.",
      register: {
        title: "Register your Temple or Trust",
        subtitle: "Create a dedicated portal for your temple administration, devotee records, and 80G tax receipts.",
        trustName: "Temple / Trust Name",
        trustNamePlaceholder: "e.g. Shri Siddheshwar Seva Trust",
        subdomain: "Choose your Subdomain",
        subdomainHelp: "3–40 lowercase letters, numbers, or hyphens. This will be your permanent temple address.",
        adminName: "Administrator Full Name",
        adminEmail: "Administrator Email",
        adminPassword: "Password",
        passwordHelp: "Must be at least 12 characters.",
        submit: "Create Temple Portal",
        submitting: "Creating portal…",
        successTitle: "Temple Portal Created!",
        successMessage: "Your temple has been registered successfully. You can now access your dedicated administrative portal.",
        openPortal: "Go to your Temple Portal",
        alreadyHaveAccount: "Already have a trust registered? Sign in here",
        invalidSlug: "Subdomain must be 3–40 lowercase letters, digits, or hyphens.",
      },
      forgot: {
        title: "Forgot your password?",
        subtitle: "For your security, password reset links are issued by your trust admin.",
        instructions: "Ask a trust admin to open Staff and choose “Reset password link” next to your name. The link works once, for one hour, and only on your trust's address.",
        backToLogin: "Back to Sign in",
      },
      reset: {
        title: "Reset your password",
        subtitle: "Choose a new strong password for your account (minimum 12 characters).",
        newPassword: "New Password",
        confirmPassword: "Confirm New Password",
        submit: "Reset Password",
        submitting: "Resetting…",
        mismatch: "Passwords do not match.",
        tokenMissing: "Password reset link is missing or invalid. Please request a new link.",
      },
    },
    portal: {
      title: "Devotee & Seva Portal",
      subtitle: "Offer online seva, make sacred donations, and download official receipts instantly.",
      donateTab: "Make a Donation",
      historyTab: "My Receipts & History",
      donateHeading: "Offer Seva & Donation",
      donateSubtitle: "Support temple activities, annadanam, rituals and holy seva.",
      quickAmounts: "Suggested Seva Amounts",
      customAmount: "Custom Amount (₹)",
      purpose: "Seva Purpose / Category",
      purposes: {
        general: "General Temple Fund",
        annadanam: "Annadanam / Prasadam Offering",
        puja: "Special Puja & Archana",
        construction: "Mandir Renovation & Nirman",
        gaushala: "Gaushala & Cow Protection",
        deepam: "Deepam & Holy Flowers",
      },
      paymentMode: "Payment Method",
      donorName: "Devotee / Donor Name",
      phoneOrEmail: "Mobile Number or Email",
      city: "City / Town (Optional)",
      donateButton: "Donate & Get Instant Receipt",
      donating: "Processing donation…",
      noPanNotice: "No PAN card required for general donations. Official temple receipt is generated immediately.",
      receiptSuccessTitle: "Donation Successful! Blessed by Mandir",
      receiptNumber: "Receipt Number",
      downloadReceipt: "Download Receipt",
      printReceipt: "Print Receipt",
      closeReceipt: "Close",
      loginToViewHistory: "Sign in with your mobile number or email to view past donations and receipts.",
      signInDevotee: "Devotee Sign In",
      phoneOtp: "Mobile Number (OTP)",
      emailOtp: "Email (OTP)",
      enterPhone: "Mobile Number (e.g. +91 98765 43210)",
      enterEmail: "Email Address",
      sendOtp: "Send Login OTP",
      sendingOtp: "Sending OTP…",
      enterOtp: "Enter 6-digit OTP",
      verifyOtp: "Verify & Sign In",
      verifyingOtp: "Verifying…",
      devOtpNotice: "Local Dev: Your test OTP is",
      registerPrompt: "Welcome to our Mandir! Complete your profile to proceed:",
      registerButton: "Complete Registration",
      consentNotice: "I agree to receive seva updates and receipts from the temple (DPDP 2023 compliant).",
      noDonationsYet: "No donations recorded yet on this account.",
      viewReceipt: "View Receipt",
    },
  },
  hi: {
    common: {
      loading: "लोड हो रहा है…",
      save: "सुरक्षित करें",
      saving: "सुरक्षित हो रहा है…",
      cancel: "रद्द करें",
      delete: "हटाएं",
      deleting: "हटाया जा रहा है…",
      edit: "संपादित करें",
      back: "वापस",
      signOut: "लॉग आउट",
      required: "अनिवार्य",
      optional: "वैकल्पिक",
      search: "खोजें",
      close: "बंद करें",
      confirm: "पुष्टि करें",
      error: "त्रुटि",
      success: "सफल",
      permanentAction: "यह क्रिया पूर्ववत नहीं की जा सकती।",
    },
    nav: {
      devotees: "भक्त (Devotees)",
      staff: "कर्मचारी (Staff)",
      donations: "दान (Donations)",
      home: "मुख्य पृष्ठ",
      profile: "प्रोफ़ाइल (Profile)",
    },
    roles: {
      TRUST_ADMIN: "ट्रस्ट प्रबंधक (Admin)",
      LEADER: "कार्यकारी (Leader)",
      MEMBER: "सदस्य (Member)",
    },
    consent: {
      IN_PERSON: "व्यक्तिगत रूप से (In Person)",
      PHONE: "फ़ोन द्वारा (Phone)",
      ONLINE_FORM: "ऑनलाइन फ़ॉर्म (Online)",
      WRITTEN: "लिखित रूप में (Written)",
    },
    profile: {
      title: "मेरी प्रोफ़ाइल (Profile)",
      description: "अपने खाते का विवरण, सुरक्षा सेटिंग्स, गोपनीयता और सूचना प्राथमिकताएं प्रबंधित करें।",
      tabs: {
        account: "खाता",
        security: "सुरक्षा व पासवर्ड",
        notifications: "सूचनाएं",
      },
      account: {
        heading: "खाता विवरण",
        role: "भूमिका",
        tenant: "ट्रस्ट सबडोमेन",
        email: "ईमेल पता",
        memberSince: "सदस्यता आरंभ",
        status: "खाता स्थिति",
        displayName: "प्रदर्शित नाम",
        displayNamePlaceholder: "उदा. रमेश शर्मा",
        saveDisplayName: "नाम सुरक्षित करें",
        savedSuccess: "प्रदर्शित नाम सफलतापूर्वक अपडेट किया गया।",
      },
      security: {
        heading: "पासवर्ड बदलें",
        description: "ट्रस्ट के डेटा और अपने खाते की सुरक्षा हेतु कम से कम 12 अक्षरों का सुदृढ़ पासवर्ड चुनें।",
        currentPassword: "वर्तमान पासवर्ड",
        newPassword: "नया पासवर्ड",
        confirmPassword: "नए पासवर्ड की पुष्टि करें",
        newPasswordHint: "कम से कम 12 अक्षर। सामान्य शब्दों से बचें।",
        savePassword: "पासवर्ड बदलें",
        savingPassword: "पासवर्ड अपडेट हो रहा है…",
        passwordMismatch: "नए पासवर्ड मेल नहीं खाते हैं।",
        passwordTooShort: "पासवर्ड कम से कम 12 अक्षरों का होना चाहिए।",
        passwordSame: "नया पासवर्ड वर्तमान पासवर्ड से भिन्न होना चाहिए।",
        savedSuccess: "आपका पासवर्ड सफलतापूर्वक बदल दिया गया है।",
      },
      notifications: {
        heading: "सूचना प्राथमिकताएं",
        description: "चुनें कि आप कौन से अपडेट और अलर्ट प्राप्त करना चाहते हैं।",
        devoteesTitle: "भक्त संबंधी अपडेट",
        devoteesDesc: "नए भक्त जुड़ने या बल्क आयात पूर्ण होने पर सूचनाएं।",
        donationsTitle: "दान एवं 80G रसीद सूचनाएं",
        donationsDesc: "दान दर्ज होने या कर रसीदें जारी होने पर सूचनाएं।",
        securityTitle: "सुरक्षा अलर्ट",
        securityDesc: "नए लॉगिन या पासवर्ड परिवर्तन पर तत्काल सुरक्षा सूचना।",
        saveNotifications: "प्राथमिकताएं सुरक्षित करें",
        savedSuccess: "सूचना प्राथमिकताएं सफलतापूर्वक अपडेट की गईं।",
      },
      avatar: {
        heading: "प्रोफ़ाइल फ़ोटो",
        changePhoto: "फ़ोटो बदलें",
        uploadPhoto: "फ़ोटो अपलोड करें",
        uploading: "अपलोड हो रहा है…",
        removePhoto: "फ़ोटो हटाएं",
        removing: "हटाया जा रहा है…",
        photoRemoved: "प्रोफ़ाइल फ़ोटो हटा दी गई।",
        photoUploaded: "प्रोफ़ाइल फ़ोटो सफलतापूर्वक अपडेट की गई।",
        hint: "PNG, JPEG, या WebP। अधिकतम 2 MB।",
        tooLarge: "फ़ोटो का आकार 2 MB या उससे कम होना चाहिए।",
        invalidFormat: "केवल PNG, JPEG और WebP छवियों की अनुमति है।",
      },
    },
    devotees: {
      title: "भक्त सूची (Devotees)",
      description: "ट्रस्ट से जुड़े भक्तजन, सहमति के आधार पर दर्ज।",
      addDevotee: "नया भक्त जोड़ें",
      exportCsv: "CSV एक्सपोर्ट",
      exporting: "एक्सपोर्ट हो रहा है…",
      importCsv: "CSV इम्पोर्ट",
      searchPlaceholderMember: "नाम से भक्त खोजें…",
      searchPlaceholderLeader: "नाम, फ़ोन या ईमेल द्वारा खोजें…",
      searchButton: "खोजें",
      clearSearch: "साफ़ करें",
      colName: "नाम",
      colPhone: "फ़ोन",
      colEmail: "ईमेल",
      colCity: "शहर",
      colState: "राज्य",
      colPincode: "पिनकोड",
      colDob: "जन्म तिथि",
      colConsent: "सहमति",
      colAdded: "जोड़ा गया",
      contactHiddenNote: "आपकी भूमिका के लिए संपर्क विवरण छिपाए गए हैं।",
      maskedLabel: "गोपनीय",
      maskedNoteTitle: "आपकी भूमिका के लिए संपर्क विवरण सीमित हैं",
      maskedNoteBody: "सदस्य के रूप में आपको नाम, शहर और राज्य पूर्ण रूप से दिखते हैं; फ़ोन नंबर केवल अंतिम 4 अंक और ईमेल केवल पहला अक्षर प्रदर्शित करता है। पता, पिनकोड और जन्म तिथि छिपे हुए हैं। खोज केवल नामों पर काम करती है।",
      emptyState: "कोई भक्त रिकॉर्ड नहीं मिला।",
      pageShowing: (start, end, total) => `कुल ${total} में से ${start}–${end} भक्त प्रदर्शित`,
      prev: "पिछला",
      next: "अगला",
      form: {
        newTitle: "नया भक्त रिकॉर्ड जोड़ें",
        newDescription: "नए भक्त का विवरण दर्ज करें। व्यक्तिगत विवरण दर्ज करने से पूर्व सहमति अनिवार्य है।",
        editTitle: "भक्त विवरण संशोधित करें",
        fullNameLabel: "पूरा नाम",
        fullNamePlaceholder: "उदा. रमेश शर्मा",
        phoneLabel: "फ़ोन नंबर",
        phonePlaceholder: "उदा. 98765 43210",
        emailLabel: "ईमेल पता",
        emailPlaceholder: "उदा. ramesh@example.org",
        addressLabel: "पता",
        addressPlaceholder: "उदा. 12 मंदिर मार्ग",
        cityLabel: "शहर",
        cityPlaceholder: "उदा. वाराणसी",
        stateLabel: "राज्य",
        statePlaceholder: "उदा. उत्तर प्रदेश",
        pincodeLabel: "पिनकोड",
        pincodePlaceholder: "उदा. 221001",
        dobLabel: "जन्म तिथि",
        consentLabel: "सहमति स्रोत",
        consentHelp: "इस व्यक्ति ने अपना विवरण सुरक्षित रखने की सहमति कैसे दी?",
        saveButton: "सुरक्षित करें",
        savingButton: "सुरक्षित हो रहा है…",
      },
      detail: {
        contactDetails: "संपर्क विवरण",
        addressHeading: "पता",
        consentHeading: "सहमति रिकॉर्ड",
        consentRecordedAt: "सहमति दर्ज समय",
        consentRecordedBy: "सहमति दर्जकर्ता",
        auditHeading: "ऑडिट रिकॉर्ड",
        createdAt: "बनाया गया",
        updatedAt: "अंतिम संशोधन",
        eraseButton: "भक्त रिकॉर्ड हटाएं",
        eraseConfirmTitle: "क्या आप इस भक्त का रिकॉर्ड हटाना चाहते हैं?",
        eraseConfirmMessage: "इससे इस भक्त का संपूर्ण व्यक्तिगत विवरण स्थायी रूप से मिटा दिया जाएगा। यह क्रिया वापस नहीं ली जा सकती।",
        eraseConfirmAction: "स्थायी रूप से हटाएं",
      },
      importPage: {
        title: "भक्तों की सूची आयात करें (CSV Import)",
        description: "CSV फ़ाइल के माध्यम से एक साथ कई भक्तों का विवरण जोड़ें। प्रत्येक पंक्ति की फ़ॉर्म नियमों के तहत जाँच होती है। किसी भी पंक्ति में त्रुटि होने पर कोई भी रिकॉर्ड नहीं जोड़ा जाएगा।",
        backLink: "← सभी भक्त",
        formatTitle: "फ़ाइल प्रारूप (File Format)",
        formatDescription: "हेडर पंक्ति और इन कॉलमों के साथ UTF-8 CSV फ़ाइल। प्रत्येक पंक्ति में केवल fullName और consentSource अनिवार्य हैं।",
        downloadTemplate: "नमूना टेम्पलेट डाउनलोड करें",
        fileLabel: "CSV फ़ाइल चुनें",
        importButton: "आयात करें (Import)",
        importing: "आयात हो रहा है…",
        importCompleteSingle: "1 भक्त का रिकॉर्ड सफलतापूर्वक जोड़ा गया।",
        importCompleteMultiple: (count) => `${count} भक्तों के रिकॉर्ड सफलतापूर्वक जोड़े गए।`,
        viewDevotees: "भक्त सूची देखें",
        rowsToFix: "सुधार हेतु पंक्तियाँ (Rows to fix)",
        colLine: "पंक्ति (Line)",
        colColumn: "कॉलम (Column)",
        colProblem: "समस्या (Problem)",
        chooseFileError: "पहले एक CSV फ़ाइल चुनें।",
      },
    },
    donations: {
      title: "दान बहीखाता (Donations)",
      description: "सभी ऑनलाइन भक्त सेवा और काउंटर से प्राप्त दानों का रीयल-टाइम रिकॉर्ड देखें।",
      recordDonation: "काउंटर दान दर्ज करें",
      totalCollection: "कुल संकलित राशि",
      totalEntries: "कुल दान प्रविष्टियाँ",
      filterFrom: "दिनांक से",
      filterTo: "दिनांक तक",
      allModes: "सभी भुगतान माध्यम",
      colDate: "दिनांक",
      colDonor: "दानदाता का नाम",
      colAmount: "राशि",
      colMode: "माध्यम",
      colPurpose: "सेवा / प्रयोजन",
      colSource: "स्रोत",
      colActions: "कार्रवाई",
      sourceOnline: "ऑनलाइन पोर्टल",
      sourceCounter: "काउंटर / कार्यालय",
      receiptButton: "रसीद",
      reverseButton: "रद्द करें",
      emptyState: "चयनित अवधि के लिए कोई दान दर्ज नहीं है।",
      recordModalTitle: "काउंटर / ऑफ़लाइन दान दर्ज करें",
      donorNameLabel: "दानदाता का नाम",
      amountLabel: "राशि (₹)",
      modeLabel: "भुगतान माध्यम",
      purposeLabel: "सेवा / प्रयोजन (वैकल्पिक)",
      referenceLabel: "संदर्भ / चेक नंबर / यूपीआई आईडी (वैकल्पिक)",
      dateLabel: "प्राप्ति तिथि",
      submitRecord: "दान दर्ज करें",
      recording: "दर्ज हो रहा है…",
      reversalModalTitle: "दान प्रविष्टि रद्द करें",
      reversalReasonLabel: "रद्द करने का कारण (कम से कम 10 अक्षर)",
      submitReversal: "रद्दीकरण की पुष्टि करें",
      reversing: "रद्द किया जा रहा है…",
      pageShowing: (start, end, total) => `${total} में से ${start} से ${end} दान प्रदर्शित`,
    },
    errors: {
      invalid_credentials: "ईमेल और पासवर्ड मेल नहीं खाते।",
      too_many_attempts: "अत्यधिक प्रयास। कृपया कुछ मिनट बाद पुनः प्रयास करें।",
      email_taken: "इस ईमेल के साथ पहले से ही एक कर्मचारी मौजूद है।",
      last_admin: "ट्रस्ट में कम से कम एक सक्रिय ट्रस्ट प्रबंधक आवश्यक है।",
      not_pending: "यह खाता पहले ही सक्रिय हो चुका है।",
      invalid_or_expired_link: "यह सेटअप लिंक अमान्य या समाप्त हो चुका है। अपने व्यवस्थापक से नया लिंक मांगें।",
      not_found: "रिकॉर्ड नहीं मिला (या हटा दिया गया है)।",
      validation_failed: "कृपया त्रुटियों को सुधारें।",
      concurrent_modification: "किसी अन्य उपयोगकर्ता ने इस रिकॉर्ड को बदल दिया है। कृपया पृष्ठ पुनः लोड करें।",
      upload_too_large: "फ़ाइल बहुत बड़ी है (अधिकतम 2 MB)।",
      malformed_upload: "फ़ाइल पढ़ने योग्य नहीं है। कृपया फ़ाइल पुनः चुनें।",
      too_many_rows: "फ़ाइल में बहुत अधिक पंक्तियाँ हैं (अधिकतम 5,000)।",
      malformed_csv: "यह मान्य CSV नहीं है। कृपया इसे CSV (UTF-8) में सहेजें।",
      empty: "फ़ाइल में कोई डेटा पंक्ति नहीं है।",
      invalid_rows: "कुछ पंक्तियों में त्रुटियाँ हैं, अतः कोई डेटा आयात नहीं किया गया। नीचे विवरण देखें।",
      bad_header: "हेडर कॉलम अपेक्षित प्रारूप से मेल नहीं खाते हैं।",
      backend_unavailable: "सेवा वर्तमान में अनुपलब्ध है। कृपया कुछ क्षणों बाद पुनः प्रयास करें।",
      network_error: "सर्वर से संपर्क नहीं हो पा रहा है। इंटरनेट कनेक्शन की जाँच करें।",
      csrf_unavailable: "सुरक्षा टोकन अनुपलब्ध है। कृपया पृष्ठ पुनः लोड करें।",
    },
    auth: {
      signIn: "लॉग इन करें",
      forgotPassword: "पासवर्ड भूल गए?",
      registerTrust: "नया ट्रस्ट / मंदिर पंजीकृत करें",
      loginTitle: "लॉग इन",
      email: "ईमेल",
      password: "पासवर्ड",
      signInButton: "लॉग इन करें",
      noTenantTitle: "अपने ट्रस्ट के पते पर जाएं",
      noTenantDesc: "प्रत्येक मंदिर या ट्रस्ट अपने समर्पित पते पर लॉग इन करता है, जैसे yourtrust.sevacenter.app।",
      noTenantLocalNote: "स्थानीय विकास के लिए yourtrust.localhost:3000 का उपयोग करें।",
      noTenantRegisterButton: "नया मंदिर या ट्रस्ट पंजीकृत करें",
      newStaffHelp: "नए कर्मचारियों को उनके ट्रस्ट व्यवस्थापक से एकमुश्त सेटअप लिंक प्राप्त होता है।",
      invalidCredentials: "ईमेल या पासवर्ड गलत है।",
      rateLimited: "लॉग इन के बहुत अधिक प्रयास किए गए। कृपया कुछ मिनट प्रतीक्षा करें और पुनः प्रयास करें।",
      setupDone: "आपका पासवर्ड सेट हो गया है। जारी रखने के लिए लॉग इन करें।",
      resetDone: "आपका पासवर्ड रीसेट कर दिया गया है। जारी रखने के लिए लॉग इन करें।",
      register: {
        title: "अपने मंदिर या ट्रस्ट को पंजीकृत करें",
        subtitle: "अपने मंदिर प्रशासन, भक्त रिकॉर्ड और 80G कर रसीदों के लिए एक समर्पित पोर्टल बनाएं।",
        trustName: "मंदिर / ट्रस्ट का नाम",
        trustNamePlaceholder: "उदा. श्री सिद्धेश्वर सेवा ट्रस्ट",
        subdomain: "अपना सबडोमेन चुनें",
        subdomainHelp: "3–40 छोटे अक्षर, अंक या हाइफ़न। यह आपके मंदिर का स्थायी पता होगा।",
        adminName: "प्रशासक का पूरा नाम",
        adminEmail: "प्रशासक का ईमेल",
        adminPassword: "पासवर्ड",
        passwordHelp: "कम से कम 12 अक्षर का होना चाहिए।",
        submit: "मंदिर पोर्टल बनाएं",
        submitting: "पोर्टल बनाया जा रहा है…",
        successTitle: "मंदिर पोर्टल सफलतापूर्वक तैयार!",
        successMessage: "आपका मंदिर सफलतापूर्वक पंजीकृत हो गया है। अब आप अपने प्रशासनिक पोर्टल पर लॉग इन कर सकते हैं।",
        openPortal: "अपने मंदिर पोर्टल पर जाएं",
        alreadyHaveAccount: "क्या ट्रस्ट पहले से पंजीकृत है? यहाँ लॉग इन करें",
        invalidSlug: "सबडोमेन में 3–40 छोटे अक्षर, अंक या हाइफ़न होने चाहिए।",
      },
      forgot: {
        title: "पासवर्ड भूल गए?",
        subtitle: "आपकी सुरक्षा के लिए, पासवर्ड रीसेट लिंक आपके ट्रस्ट एडमिन द्वारा दिए जाते हैं।",
        instructions: "किसी ट्रस्ट एडमिन से कहें कि वे स्टाफ़ खोलें और आपके नाम के आगे “पासवर्ड रीसेट लिंक” चुनें। यह लिंक केवल एक बार, एक घंटे के लिए और केवल आपके ट्रस्ट के पते पर काम करता है।",
        backToLogin: "वापस लॉग इन पर जाएं",
      },
      reset: {
        title: "अपना पासवर्ड रीसेट करें",
        subtitle: "अपने खाते के लिए एक नया मजबूत पासवर्ड चुनें (कम से कम 12 अक्षर)।",
        newPassword: "नया पासवर्ड",
        confirmPassword: "नए पासवर्ड की पुष्टि करें",
        submit: "पासवर्ड रीसेट करें",
        submitting: "रीसेट हो रहा है…",
        mismatch: "पासवर्ड मेल नहीं खा रहे हैं।",
        tokenMissing: "पासवर्ड रीसेट लिंक अमान्य या गायब है। कृपया नया लिंक मांगें।",
      },
    },
    portal: {
      title: "भक्त एवं सेवा पोर्टल",
      subtitle: "ऑनलाइन सेवा अर्पित करें, पवित्र दान करें और तुरंत आधिकारिक रसीद प्राप्त करें।",
      donateTab: "दान / सेवा अर्पित करें",
      historyTab: "मेरी रसीदें एवं इतिहास",
      donateHeading: "पवित्र सेवा एवं दान",
      donateSubtitle: "मंदिर गतिविधियों, अन्नदान, दैनिक पूजा-अर्चना और धार्मिक सेवा में सहयोग दें।",
      quickAmounts: "सुझावित सेवा राशि",
      customAmount: "अपनी इच्छानुसार राशि (₹)",
      purpose: "सेवा का उद्देश्य / श्रेणी",
      purposes: {
        general: "सामान्य मंदिर निधि",
        annadanam: "अन्नदानम् / महाप्रसाद सेवा",
        puja: "विशेष पूजा एवं अर्चना",
        construction: "मंदिर निर्माण एवं जीर्णोद्धार",
        gaushala: "गौशाला एवं गो-सेवा",
        deepam: "अखंड दीप एवं पुष्प सेवा",
      },
      paymentMode: "भुगतान का माध्यम",
      donorName: "भक्त / दानदाता का नाम",
      phoneOrEmail: "मोबाइल नंबर या ईमेल",
      city: "शहर / नगर (वैकल्पिक)",
      donateButton: "दान करें एवं तुरंत रसीद पाएं",
      donating: "दान प्रक्रिया में है…",
      noPanNotice: "सामान्य दान के लिए पैन कार्ड की आवश्यकता नहीं है। मंदिर की आधिकारिक रसीद तुरंत प्रदान की जाएगी।",
      receiptSuccessTitle: "दान सफल! मंदिर का आशीर्वाद",
      receiptNumber: "रसीद संख्या",
      downloadReceipt: "रसीद डाउनलोड करें",
      printReceipt: "रसीद प्रिंट करें",
      closeReceipt: "बंद करें",
      loginToViewHistory: "अपने पिछले दान और रसीदें देखने के लिए अपने मोबाइल नंबर या ईमेल से लॉग इन करें।",
      signInDevotee: "भक्त लॉग इन",
      phoneOtp: "मोबाइल नंबर (OTP)",
      emailOtp: "ईमेल (OTP)",
      enterPhone: "मोबाइल नंबर (उदा. +91 98765 43210)",
      enterEmail: "ईमेल पता",
      sendOtp: "लॉग इन OTP भेजें",
      sendingOtp: "OTP भेजा जा रहा है…",
      enterOtp: "6 अंकों का OTP दर्ज करें",
      verifyOtp: "सत्यापित करें एवं लॉग इन करें",
      verifyingOtp: "सत्यापित हो रहा है…",
      devOtpNotice: "लोकल डेवलपमेंट: आपका टेस्ट OTP है",
      registerPrompt: "हमारे मंदिर में आपका स्वागत है! कृपया अपनी जानकारी पूरी करें:",
      registerButton: "रजिस्ट्रेशन पूरा करें",
      consentNotice: "मैं मंदिर से सेवा संदेश और रसीदें प्राप्त करने की सहमति देता/देती हूँ (DPDP 2023 अनुपालन)।",
      noDonationsYet: "इस खाते पर अभी तक कोई दान दर्ज नहीं है।",
      viewReceipt: "रसीद देखें",
    },
  },
};

export const LANG_COOKIE = "sc_lang";

export function getInitialLanguage(): Language {
  if (typeof document !== "undefined") {
    const match = document.cookie.match(new RegExp(`(?:^|; )${LANG_COOKIE}=([^;]*)`));
    if (match && (match[1] === "hi" || match[1] === "en")) {
      return match[1] as Language;
    }
  }
  return "en";
}

export function persistLanguage(lang: Language): void {
  if (typeof document !== "undefined") {
    // 1-year persistence
    document.cookie = `${LANG_COOKIE}=${lang}; path=/; max-age=31536000; SameSite=Lax`;
    document.documentElement.lang = lang;
  }
}
