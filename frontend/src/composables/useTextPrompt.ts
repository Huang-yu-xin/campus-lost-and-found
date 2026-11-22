import { shallowRef } from 'vue'

interface TextPrompt {
  message: string
  title: string
  inputValidator: (value: string) => true | string
  resolve: (result: { value: string }) => void
  cancel: (reason: 'cancel') => void
}

export const textPrompt = shallowRef<TextPrompt>()

export function askForText(
  message: string,
  title: string,
  { inputValidator }: Pick<TextPrompt, 'inputValidator'>,
) {
  return new Promise<{ value: string }>((resolve, cancel) => {
    if (textPrompt.value) {
      cancel('cancel')
      return
    }
    textPrompt.value = { message, title, inputValidator, resolve, cancel }
  })
}
