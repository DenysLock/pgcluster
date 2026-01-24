import { Pipe, PipeTransform } from '@angular/core';

/**
 * Pipe to format byte values into human-readable format.
 *
 * Usage:
 *   {{ 1024 | formatBytes }}         => "1.00 KB"
 *   {{ 1536 | formatBytes:1 }}       => "1.5 KB"
 *   {{ null | formatBytes }}         => "0 B"
 */
@Pipe({
  name: 'formatBytes',
  standalone: true
})
export class FormatBytesPipe implements PipeTransform {
  private readonly units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

  transform(bytes: number | null | undefined, decimals: number = 2): string {
    if (bytes === null || bytes === undefined || bytes === 0) {
      return '0 B';
    }

    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    const index = Math.min(i, this.units.length - 1);

    return `${parseFloat((bytes / Math.pow(k, index)).toFixed(dm))} ${this.units[index]}`;
  }
}
