/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.autofill

// TODO(b/383201236): This is replaced by ContentType. It was experimental in 1.7 so mark it
//  deprecated before 1.8.
/**
 * Autofill type information.
 *
 * Autofill services use the [AutofillType] to determine what value to use to autofill fields
 * associated with this type. If the [AutofillType] is not specified, the autofill services have to
 * use heuristics to determine the right value to use while auto-filling the corresponding field.
 *
 * This has been deprecated in favor of a new semantics based API. Use
 * [ContentType][androidx.compose.ui.semantics.SemanticsProperties.ContentType] instead.
 */
@Deprecated("Use the new semantics-based API and androidx.compose.ui.autofill.ContentType instead.")
enum class AutofillType {
    /** Indicates that the associated component can be auto-filled with an email address. */
    EmailAddress,

    /** Indicates that the associated component can be auto-filled with a username. */
    Username,

    /** Indicates that the associated component can be auto-filled with a password. */
    Password,

    /**
     * Indicates that the associated component can be interpreted as a newly created username for
     * save/update.
     */
    NewUsername,

    /**
     * Indicates that the associated component can be interpreted as a newly created password for
     * save/update.
     */
    NewPassword,

    /** Indicates that the associated component can be auto-filled with a postal address. */
    PostalAddress,

    /** Indicates that the associated component can be auto-filled with a postal code. */
    PostalCode,

    /** Indicates that the associated component can be auto-filled with a credit card number. */
    CreditCardNumber,

    /**
     * Indicates that the associated component can be auto-filled with a credit card security code.
     */
    CreditCardSecurityCode,

    /**
     * Indicates that the associated component can be auto-filled with a credit card expiration
     * date.
     */
    CreditCardExpirationDate,

    /**
     * Indicates that the associated component can be auto-filled with a credit card expiration
     * month.
     */
    CreditCardExpirationMonth,

    /**
     * Indicates that the associated component can be auto-filled with a credit card expiration
     * year.
     */
    CreditCardExpirationYear,

    /**
     * Indicates that the associated component can be auto-filled with a credit card expiration day.
     */
    CreditCardExpirationDay,

    /** Indicates that the associated component can be auto-filled with a country name/code. */
    AddressCountry,

    /** Indicates that the associated component can be auto-filled with a region/state. */
    AddressRegion,

    /**
     * Indicates that the associated component can be auto-filled with an address locality
     * (city/town).
     */
    AddressLocality,

    /** Indicates that the associated component can be auto-filled with a street address. */
    AddressStreet,

    /**
     * Indicates that the associated component can be auto-filled with auxiliary address details.
     */
    AddressAuxiliaryDetails,

    /**
     * Indicates that the associated component can be auto-filled with an extended ZIP/POSTAL code.
     *
     * Example: In forms that split the U.S. ZIP+4 Code with nine digits 99999-9999 into two fields
     * annotate the delivery route code with this hint.
     */
    PostalCodeExtended,

    /** Indicates that the associated component can be auto-filled with a person's full name. */
    PersonFullName,

    /**
     * Indicates that the associated component can be auto-filled with a person's first/given name.
     */
    PersonFirstName,

    /**
     * Indicates that the associated component can be auto-filled with a person's last/family name.
     */
    PersonLastName,

    /** Indicates that the associated component can be auto-filled with a person's middle name. */
    PersonMiddleName,

    /**
     * Indicates that the associated component can be auto-filled with a person's middle initial.
     */
    PersonMiddleInitial,

    /** Indicates that the associated component can be auto-filled with a person's name prefix. */
    PersonNamePrefix,

    /** Indicates that the associated component can be auto-filled with a person's name suffix. */
    PersonNameSuffix,

    /**
     * Indicates that the associated component can be auto-filled with a phone number with country
     * code.
     *
     * Example: +1 123-456-7890
     */
    PhoneNumber,

    /**
     * Indicates that the associated component can be auto-filled with the current device's phone
     * number usually for Sign Up / OTP flows.
     */
    PhoneNumberDevice,

    /**
     * Indicates that the associated component can be auto-filled with a phone number's country
     * code.
     */
    PhoneCountryCode,

    /**
     * Indicates that the associated component can be auto-filled with a phone number without
     * country code.
     */
    PhoneNumberNational,

    /** Indicates that the associated component can be auto-filled with a gender. */
    Gender,

    /** Indicates that the associated component can be auto-filled with a full birth date. */
    BirthDateFull,

    /**
     * Indicates that the associated component can be auto-filled with a birth day(of the month).
     */
    BirthDateDay,

    /** Indicates that the associated component can be auto-filled with a birth month. */
    BirthDateMonth,

    /** Indicates that the associated component can be auto-filled with a birth year. */
    BirthDateYear,

    /**
     * Indicates that the associated component can be auto-filled with a SMS One Time Password
     * (OTP).
     *
     * TODO(b/153386346): Support use-case where you specify the start and end index of the OTP.
     */
    SmsOtpCode,
}
