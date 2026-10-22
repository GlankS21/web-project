import { useTranslations } from "next-intl";
import { Field } from "@/components/ui";
import { formatPhone, normalizePhone, PHONE_INPUT_PATTERN } from "@/lib/phone";
import type { AddressFields as Address } from "@/lib/types";

// Kept here for the pages that render an address next to these inputs
export { formatAddress } from "@/lib/address";

/** The shop delivers in Russia only. */
export const DEFAULT_COUNTRY = "RU";

interface Props {
  /** Prefix of the input ids, so two address forms can share a page. */
  idPrefix: string;
  defaults?: Partial<Address>;
  /** Validation message of a field, by the API field name (without any request prefix). */
  error?: (field: keyof Address) => string | undefined;
}

/**
 * Inputs of a delivery address, named like the API fields; read them with {@link readAddress}.
 * For Russian addresses "district" holds the region (область, край) and "ward" the city district.
 */
export function AddressFields({ idPrefix, defaults, error }: Props) {
  const t = useTranslations("addressFields");
  const tCommon = useTranslations("common");
  const id = (name: string) => `${idPrefix}-${name}`;
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label={t("recipientName")} htmlFor={id("recipientName")} error={error?.("recipientName")} required>
        <input
          id={id("recipientName")}
          name="recipientName"
          required
          maxLength={150}
          defaultValue={defaults?.recipientName}
          placeholder={t("recipientNamePlaceholder")}
          autoComplete="name"
          className="input"
        />
      </Field>
      <Field
        label={t("phoneNumber")}
        htmlFor={id("phoneNumber")}
        error={error?.("phoneNumber")}
        hint={t("phoneHint")}
        required
      >
        <input
          id={id("phoneNumber")}
          name="phoneNumber"
          type="tel"
          inputMode="tel"
          required
          pattern={PHONE_INPUT_PATTERN}
          maxLength={18}
          placeholder={tCommon("phonePlaceholder")}
          title={t("phoneHint")}
          defaultValue={formatPhone(defaults?.phoneNumber) ?? undefined}
          autoComplete="tel"
          className="input"
        />
      </Field>
      <Field label={t("city")} htmlFor={id("city")} error={error?.("city")} required>
        <input
          id={id("city")}
          name="city"
          required
          maxLength={100}
          defaultValue={defaults?.city}
          autoComplete="address-level2"
          className="input"
        />
      </Field>
      <Field label={t("district")} htmlFor={id("district")} error={error?.("district")} hint={t("districtHint")}>
        <input
          id={id("district")}
          name="district"
          maxLength={100}
          defaultValue={defaults?.district ?? ""}
          autoComplete="address-level1"
          className="input"
        />
      </Field>
      <Field label={t("addressLine1")} htmlFor={id("addressLine1")} error={error?.("addressLine1")} required className="sm:col-span-2">
        <input
          id={id("addressLine1")}
          name="addressLine1"
          required
          maxLength={255}
          defaultValue={defaults?.addressLine1}
          placeholder={t("addressLine1Placeholder")}
          autoComplete="address-line1"
          className="input"
        />
      </Field>
      <Field label={t("addressLine2")} htmlFor={id("addressLine2")} error={error?.("addressLine2")} className="sm:col-span-2">
        <input
          id={id("addressLine2")}
          name="addressLine2"
          maxLength={255}
          defaultValue={defaults?.addressLine2 ?? ""}
          placeholder={t("addressLine2Placeholder")}
          autoComplete="address-line2"
          className="input"
        />
      </Field>
      <Field label={t("ward")} htmlFor={id("ward")} error={error?.("ward")}>
        <input
          id={id("ward")}
          name="ward"
          maxLength={100}
          defaultValue={defaults?.ward ?? ""}
          autoComplete="address-level3"
          className="input"
        />
      </Field>
      <Field label={t("postalCode")} htmlFor={id("postalCode")} error={error?.("postalCode")} hint={t("postalCodeHint")}>
        <input
          id={id("postalCode")}
          name="postalCode"
          inputMode="numeric"
          pattern="\d{6}"
          maxLength={6}
          title={t("postalCodeHint")}
          defaultValue={defaults?.postalCode ?? ""}
          autoComplete="postal-code"
          className="input"
        />
      </Field>
      <input type="hidden" name="countryCode" value={defaults?.countryCode ?? DEFAULT_COUNTRY} />
    </div>
  );
}

/** The address entered in a form rendered with {@link AddressFields}; empty optional fields are null. */
export function readAddress(form: FormData): Address {
  const text = (name: string) => String(form.get(name) ?? "").trim();
  const optional = (name: string) => text(name) || null;
  return {
    recipientName: text("recipientName"),
    phoneNumber: normalizePhone(text("phoneNumber")),
    addressLine1: text("addressLine1"),
    addressLine2: optional("addressLine2"),
    ward: optional("ward"),
    district: optional("district"),
    city: text("city"),
    postalCode: optional("postalCode"),
    countryCode: text("countryCode").toUpperCase() || DEFAULT_COUNTRY,
  };
}
