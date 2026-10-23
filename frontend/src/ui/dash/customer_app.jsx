import React from "react";
import RegistrationPage from "./registration_page/registration_page";

// Keep PR 36's entry point while using the shared registration,
// catalog, menu management and ordering flow for every role.
export default function CustomerApp() {
  return <RegistrationPage />;
}
