export function Icon({ name, size = 24 }: { name: string; size?: number }) {
  return (
    <img
      className="asset-icon"
      src={`/assets/reader_${name}.png`}
      alt=""
      width={size}
      height={size}
    />
  );
}
