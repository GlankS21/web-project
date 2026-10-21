import { Field } from "@/components/ui";
import type { AddressFields as Address } from "@/lib/types";

const PHONE_PATTERN = "\\+?[0-9]{9,15}";

interface Props {
  /** Prefix of the input ids, so two address forms can share a page. */
  idPrefix: string;
  defaults?: Partial<Address>;
  /** Validation message of a field, by the API field name (without any request prefix). */
  error?: (field: keyof Address) => string | undefined;
}

/** Inputs of a delivery address, named like the API fields; read them with {@link readAddress}. */
export function AddressFields({ idPrefix, defaults, error }: Props) {
  const id = (name: string) => `${idPrefix}-${name}`;
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="Người nhận" htmlFor={id("recipientName")} error={error?.("recipientName")} required>
        <input
          id={id("recipientName")}
          name="recipientName"
          required
          maxLength={150}
          defaultValue={defaults?.recipientName}
          autoComplete="name"
          className="input"
        />
      </Field>
      <Field label="Số điện thoại" htmlFor={id("phoneNumber")} error={error?.("phoneNumber")} hint="9–15 chữ số, có thể bắt đầu bằng +" required>
        <input
          id={id("phoneNumber")}
          name="phoneNumber"
          type="tel"
          required
          pattern={PHONE_PATTERN}
          maxLength={16}
          defaultValue={defaults?.phoneNumber}
          autoComplete="tel"
          className="input"
        />
      </Field>
      <Field label="Địa chỉ" htmlFor={id("addressLine1")} error={error?.("addressLine1")} required className="sm:col-span-2">
        <input
          id={id("addressLine1")}
          name="addressLine1"
          required
          maxLength={255}
          defaultValue={defaults?.addressLine1}
          placeholder="Số nhà, tên đường"
          autoComplete="address-line1"
          className="input"
        />
      </Field>
      <Field label="Địa chỉ (bổ sung)" htmlFor={id("addressLine2")} error={error?.("addressLine2")} className="sm:col-span-2">
        <input
          id={id("addressLine2")}
          name="addressLine2"
          maxLength={255}
          defaultValue={defaults?.addressLine2 ?? ""}
          placeholder="Toà nhà, căn hộ..."
          autoComplete="address-line2"
          className="input"
        />
      </Field>
      <Field label="Phường/Xã" htmlFor={id("ward")} error={error?.("ward")}>
        <input id={id("ward")} name="ward" maxLength={100} defaultValue={defaults?.ward ?? ""} className="input" />
      </Field>
      <Field label="Quận/Huyện" htmlFor={id("district")} error={error?.("district")}>
        <input id={id("district")} name="district" maxLength={100} defaultValue={defaults?.district ?? ""} className="input" />
      </Field>
      <Field label="Tỉnh/Thành phố" htmlFor={id("city")} error={error?.("city")} required>
        <input
          id={id("city")}
          name="city"
          required
          maxLength={100}
          defaultValue={defaults?.city}
          autoComplete="address-level1"
          className="input"
        />
      </Field>
      <Field label="Mã bưu chính" htmlFor={id("postalCode")} error={error?.("postalCode")}>
        <input
          id={id("postalCode")}
          name="postalCode"
          maxLength={20}
          defaultValue={defaults?.postalCode ?? ""}
          autoComplete="postal-code"
          className="input"
        />
      </Field>
      <input type="hidden" name="countryCode" value={defaults?.countryCode ?? "VN"} />
    </div>
  );
}

/** The address entered in a form rendered with {@link AddressFields}; empty optional fields are null. */
export function readAddress(form: FormData): Address {
  const text = (name: string) => String(form.get(name) ?? "").trim();
  const optional = (name: string) => text(name) || null;
  return {
    recipientName: text("recipientName"),
    phoneNumber: text("phoneNumber").replace(/[\s.-]/g, ""),
    addressLine1: text("addressLine1"),
    addressLine2: optional("addressLine2"),
    ward: optional("ward"),
    district: optional("district"),
    city: text("city"),
    postalCode: optional("postalCode"),
    countryCode: text("countryCode").toUpperCase() || "VN",
  };
}

export function formatAddress(address: Address): string {
  return [address.addressLine1, address.addressLine2, address.ward, address.district, address.city]
    .filter((part) => part && part.trim())
    .join(", ");
}
